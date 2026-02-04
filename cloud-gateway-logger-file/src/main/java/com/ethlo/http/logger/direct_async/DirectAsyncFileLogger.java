package com.ethlo.http.logger.direct_async;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.rendering.AccessLogTemplateRenderer;
import com.ethlo.http.model.WebExchangeDataProvider;

public class DirectAsyncFileLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(DirectAsyncFileLogger.class);

    private final AccessLogTemplateRenderer accessLogTemplateRenderer;
    private final OutputStream destination;
    private final BlockingQueue<WebExchangeDataProvider> queue = new ArrayBlockingQueue<>(10_000);
    private final Thread writerThread;
    private volatile boolean running = true;

    /**
     * @param destination Use System.out for Docker, or a FileOutputStream for a file
     */
    public DirectAsyncFileLogger(AccessLogTemplateRenderer accessLogTemplateRenderer, OutputStream destination)
    {
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
        this.destination = new BufferedOutputStream(destination);
        this.writerThread = new Thread(this::writeLoop, "access-log-writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    @Override
    public void accessLog(WebExchangeDataProvider dataProvider)
    {
        if (!queue.offer(dataProvider))
        {
            logger.warn("Log queue full, dropping request {}", dataProvider.getRequestId());
        }
    }

    private void writeLoop()
    {
        try (destination)
        {
            while (running || !queue.isEmpty())
            {
                try
                {
                    final WebExchangeDataProvider data = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (data != null)
                    {
                        final String logLine = accessLogTemplateRenderer.render(data.asMetaMap()) + "\n";
                        destination.write(logLine.getBytes(StandardCharsets.UTF_8));

                        // Periodic flush: If queue is empty, flush what we have to the stream
                        if (queue.isEmpty())
                        {
                            destination.flush();
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (Exception e)
                {
                    // Use standard error for logger failures to avoid recursive loops
                    System.err.println("Failed to write access log: " + e.getMessage());
                }
            }
        }
        catch (IOException e)
        {
            logger.error("Critical: Access log stream closed", e);
        }
    }

    @Override
    public String getName()
    {
        return "direct_async";
    }

    @Override
    public void close() throws Exception
    {
        this.running = false;
        try
        {
            this.writerThread.join(5000);
        }
        catch (InterruptedException ignored)
        {
        }
        this.destination.close();
    }
}