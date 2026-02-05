package com.ethlo.http.model;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    public static final BodyProvider NONE = new BodyProvider("<<None>>".getBytes(StandardCharsets.UTF_8), null);

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

            if (contentEncoding == null)
            {
                return rawStream;
            }

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

    /**
     * Moves the underlying file to a new location if it exists on disk.
     * If the data is in memory, it writes the bytes to the target path.
     *
     * @param target The destination path
     * @return A new BodyProvider instance pointing to the new location
     * @throws UncheckedIOException if an I/O error occurs while creating directories, moving, or writing the file
     */
    public BodyProvider moveTo(Path target)
    {
        try
        {
            if (file != null)
            {
                // Atomically move the file if on the same partition
                Files.createDirectories(target.getParent());
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                return new BodyProvider(target, contentEncoding);
            }
            else if (bytes != null)
            {
                // If it was only in memory, persist it to the target path now
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                return new BodyProvider(target, contentEncoding);
            }
            return this; // Nothing to do
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}