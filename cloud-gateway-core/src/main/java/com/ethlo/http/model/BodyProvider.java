package com.ethlo.http.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.netty.ServerDirection;

public class BodyProvider
{
    private static final Logger logger = LoggerFactory.getLogger(BodyProvider.class);
    private final String requestId;
    private final ServerDirection serverDirection;
    private final Path file;
    private final FileChannel fileChannel;
    private final String contentEncoding;
    private final long size;

    public BodyProvider(String requestId, ServerDirection serverDirection, Path file, FileChannel fileChannel, final String contentEncoding)
    {
        this.requestId = requestId;
        this.serverDirection = serverDirection;
        this.file = file;
        this.fileChannel = Objects.requireNonNull(fileChannel);
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
            final InputStream rawStream = Files.newInputStream(this.file);

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

    public Optional<ByteBuffer> getBuffer()
    {
        if (fileChannel == null)
        {
            return Optional.empty();
        }

        try
        {
            final long fileSize = fileChannel.size();
            final ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(fileSize));
            logger.debug("Using data from buffer file {} of size {} for {} {}", file, size, serverDirection, requestId);
            fileChannel.read(buffer, 0);
            buffer.flip();
            return Optional.of(buffer);
        }
        catch (IOException e)
        {
            logger.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }
}