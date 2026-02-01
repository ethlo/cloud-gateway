package com.ethlo.http.blocking.model;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.jspecify.annotations.Nullable;

/**
 * Provides a unified way to access the request/response body,
 * regardless of whether it is stored in memory or on disk.
 */
public record BodyProvider(@Nullable Path file, byte @Nullable [] bytes, @Nullable String contentEncoding)
{
    // Constructor for Disk-based storage
    public BodyProvider(Path file, String contentEncoding)
    {
        this(Objects.requireNonNull(file), null, contentEncoding);
    }

    // Constructor for Memory-based storage
    public BodyProvider(byte[] bytes, String contentEncoding)
    {
        this(null, Objects.requireNonNull(bytes), contentEncoding);
    }

    public InputStream getInputStream()
    {
        try
        {
            final InputStream rawStream = createRawStream();

            if (contentEncoding == null) return rawStream;

            return switch (contentEncoding.toLowerCase())
            {
                case "gzip" -> new GZIPInputStream(rawStream);
                case "deflate" -> new InflaterInputStream(rawStream);
                default -> rawStream;
            };
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    private InputStream createRawStream() throws IOException
    {
        if (bytes != null)
        {
            // Data is in RAM
            return new ByteArrayInputStream(bytes);
        }
        else if (file != null)
        {
            // Data is on Disk
            return new BufferedInputStream(Files.newInputStream(file));
        }

        // Should not happen if constructed correctly
        return new ByteArrayInputStream(new byte[0]);
    }
}