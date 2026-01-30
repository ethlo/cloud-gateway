package com.ethlo.http.model;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public record BodyProvider(Path file, String contentEncoding)
{
    public InputStream getInputStream()
    {
        try
        {
            final InputStream rawStream = new BufferedInputStream(Files.newInputStream(this.file));

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
}