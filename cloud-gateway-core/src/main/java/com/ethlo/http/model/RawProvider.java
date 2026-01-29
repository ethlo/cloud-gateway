package com.ethlo.http.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.netty.ServerDirection;

public class RawProvider
{
    private static final Logger logger = LoggerFactory.getLogger(RawProvider.class);
    private final String requestId;
    private final ServerDirection serverDirection;
    private final Path file;
    private final FileChannel fileChannel;
    private final long size;

    public RawProvider(String requestId, ServerDirection serverDirection, Path file, FileChannel fileChannel)
    {
        this.requestId = requestId;
        this.serverDirection = serverDirection;
        this.file = file;
        this.fileChannel = Objects.requireNonNull(fileChannel);
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
            return Optional.of(buffer);
        }
        catch (IOException e)
        {
            logger.error(e.getMessage(), e);
            return Optional.empty();
        }
    }
}