package com.ethlo.http.model;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class BodyProvider
{
    private final Path file;
    private final String contentEncoding;
    private final long size;

    public BodyProvider(Path file, FileChannel fileChannel, final String contentEncoding)
    {
        this.file = file;
        this.contentEncoding = contentEncoding;
        try
        {
            this.size = fileChannel.size();
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    public long size()
    {
        return size;
    }

    public InputStream getInputStream()
    {
        try
        {
            final InputStream rawStream = new BufferedInputStream(Files.newInputStream(this.file));

            if ("gzip".equalsIgnoreCase(contentEncoding))
            {
                return new GZIPInputStream(rawStream);
            }
            else if ("deflate".equalsIgnoreCase(contentEncoding))
            {
                return new InflaterInputStream(rawStream);
            }

            return rawStream;
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }
}