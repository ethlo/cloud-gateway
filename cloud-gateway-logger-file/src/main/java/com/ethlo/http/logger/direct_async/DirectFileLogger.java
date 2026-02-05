package com.ethlo.http.logger.direct_async;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

import com.ethlo.http.DataBufferRepository;
import com.ethlo.http.logger.ArchiveManager;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.rendering.AccessLogTemplateRenderer;
import com.ethlo.http.model.WebExchangeDataProvider;

public class DirectFileLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(DirectFileLogger.class);

    private final AccessLogTemplateRenderer accessLogTemplateRenderer;
    private final ArchiveManager archiveManager;
    private final Path logDirectory;
    private long currentSize;
    private LocalDate currentDate;
    private OutputStream destination;

    // Config
    private long maxFileSize = DataSize.ofMegabytes(10).toBytes();

    public DirectFileLogger(AccessLogTemplateRenderer accessLogTemplateRenderer, Path logDirectory, DataBufferRepository repository)
    {
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
        this.archiveManager = new ArchiveManager(logDirectory, repository);
        this.logDirectory = Objects.requireNonNull(logDirectory);
        try
        {
            init();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Unable to initialize logger: " + e.getMessage(), e);
        }
    }

    private void init() throws IOException
    {
        Files.createDirectories(logDirectory);
        final Path activePath = logDirectory.resolve("access.log");

        this.currentDate = LocalDate.now();
        this.destination = new BufferedOutputStream(Files.newOutputStream(activePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        ));

        // Check if the existing file is already over the limit from a previous run
        this.currentSize = Files.size(activePath);
        if (currentSize > maxFileSize)
        {
            roll(currentDate);
        }
    }

    @Override
    public void accessLog(WebExchangeDataProvider data)
    {
        try
        {
            init();

            // 1. Render the text-based access log line
            final String logLine = accessLogTemplateRenderer.render(data.asMetaMap()) + "\n";

            // 2. Write to the rolling access log file (Text only)
            writeToRollingFile(logLine.getBytes(StandardCharsets.UTF_8));

            // 3. Archive the raw data (Headers + Body combined into .raw files)
            // This uses the ArchiveManager's m/l/8/h/ sharding logic
            archiveManager.archive(data);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private void writeToRollingFile(byte[] bytes) throws IOException
    {
        final LocalDate now = LocalDate.now();

        // Trigger roll if the date changed OR the file grew too large
        if (!now.equals(currentDate) || (currentSize + bytes.length > maxFileSize))
        {
            roll(now);
        }

        destination.write(bytes);
        currentSize += bytes.length;
        destination.flush();
    }

    private void roll(LocalDate newDate) throws IOException
    {
        if (destination != null)
        {
            destination.flush();
            destination.close();
        }

        final Path activeLog = logDirectory.resolve("access.log");

        if (Files.exists(activeLog))
        {
            // Add a timestamp to the rolled file: access.log.2026-02-05.143005.log
            final String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd.HHmmss").format(LocalDateTime.now());
            final Path rolledLog = logDirectory.resolve("access.log." + timestamp + ".log");
            Files.move(activeLog, rolledLog, StandardCopyOption.REPLACE_EXISTING);
        }

        // Open new buffered stream for the next batch of logs
        this.destination = new BufferedOutputStream(Files.newOutputStream(activeLog,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        ));

        this.currentSize = Files.size(activeLog);
        this.currentDate = newDate;
    }

    @Override
    public String getName()
    {
        return "direct_file";
    }

    @Override
    public void close() throws Exception
    {
        this.destination.close();
    }
}