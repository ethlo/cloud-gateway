package com.ethlo.http.logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.ethlo.http.DataBufferRepository;
import com.ethlo.http.model.WebExchangeDataProvider;

public class ArchiveManager
{
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Path baseArchiveDir;
    private final DataBufferRepository repository;

    public ArchiveManager(Path baseArchiveDir, DataBufferRepository repository)
    {
        this.baseArchiveDir = baseArchiveDir;
        this.repository = repository;
    }

    public void archive(WebExchangeDataProvider data)
    {
        final String requestId = data.getRequestId();

        // 1. Resolve the sharded path: /base/2026/02/05/m/l/8/h/
        final Path targetDir = resolvePath(requestId);

        try
        {
            // 2. Ensure the full directory tree exists
            Files.createDirectories(targetDir);

            // 3. Trigger the stitching/moving logic in the repository
            // This combines headers + body into [requestId]_request.raw and [requestId]_response.raw
            repository.archive(requestId, targetDir);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to create archive directory: " + targetDir, e);
        }
    }

    private Path resolvePath(String id)
    {
        // Date partitioning first for easy retention management
        final String datePart = DATE_FORMATTER.format(LocalDate.now());

        // 4-level sharding based on the Request ID
        // ml8hl0mc -> m/l/8/h/
        final String s1 = id.substring(0, 1);
        final String s2 = id.substring(1, 2);
        final String s3 = id.substring(2, 3);
        final String s4 = id.substring(3, 4);

        return baseArchiveDir.resolve(datePart)
                .resolve(s1).resolve(s2).resolve(s3).resolve(s4)
                .resolve(id); // Final sub-folder for this specific exchange
    }
}