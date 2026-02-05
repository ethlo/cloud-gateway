package com.ethlo.http.logger.delegate;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.ethlo.chronograph.Chronograph;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;

public class AsyncDelegateLogger extends BaseDelegateHttpLogger implements Runnable
{
    private final BlockingQueue<WebExchangeDataProvider> queue = new ArrayBlockingQueue<>(10_000);
    private final Thread workerThread;
    private volatile boolean running = true;

    public AsyncDelegateLogger(final List<HttpLogger> httpLoggers)
    {
        super(httpLoggers);
        this.workerThread = new Thread(this, "http-logger-worker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    @Override
    public void accessLog(final Chronograph chronograph, final WebExchangeDataProvider dataProvider)
    {
        chronograph.time("async_log", () ->
                {
                    if (!queue.offer(dataProvider))
                    {
                        logger.warn("Access log queue full, dropping request {}", dataProvider.getRequestId());
                    }
                }
        );
    }

    @Override
    public void run()
    {
        while (running || !queue.isEmpty())
        {
            try
            {
                final WebExchangeDataProvider data = queue.poll(1, TimeUnit.SECONDS);
                if (data != null)
                {
                    final Chronograph asyncChronograph = Chronograph.create();
                    logWebExchangeData(asyncChronograph, data);
                    data.cleanup();
                    logger.debug("Request {}:\n{}", data.getRequestId(), asyncChronograph);
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception e)
            {
                logger.error("Error in async logger worker", e);
            }
        }
    }

    @Override
    public void close() throws Exception
    {
        this.running = false;
        try
        {
            workerThread.join(5000);
        }
        catch (InterruptedException ignored)
        {
        }
        super.close();
    }
}