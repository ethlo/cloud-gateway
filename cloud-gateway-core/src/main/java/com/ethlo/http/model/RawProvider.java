package com.ethlo.http.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.netty.ServerDirection;

public class RawProvider
{
    private static final Logger logger = LoggerFactory.getLogger(RawProvider.class);
    private final String requestId;
    private final ServerDirection serverDirection;
    private final Path file;
    private final AsynchronousFileChannel fileChannel;
    private final long size;

    public RawProvider(String requestId, ServerDirection serverDirection, Path file, long size)
    {
        this.requestId = requestId;
        this.serverDirection = serverDirection;
        this.file = file;
        this.size = size;
        this.fileChannel = null;
    }

    public RawProvider(String requestId, ServerDirection serverDirection, Path file, AsynchronousFileChannel fileChannel)
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

    /**
     * Stream the captured bytes straight from the buffer file, for consumers that should not hold the whole capture in memory.
     */
    public Optional<InputStream> openStream() throws IOException
    {
        if (fileChannel == null)
        {
            return Optional.empty();
        }
        return Optional.of(Files.newInputStream(file));
    }

    public Optional<CompletableFuture<ByteBuffer>> getBuffer()
    {
        if (fileChannel == null)
        {
            return Optional.empty();
        }

        // Read the whole file in one go rather than issuing a single asynchronous read, which is not
        // guaranteed to fill the buffer and would silently yield truncated, zero-padded content.
        final CompletableFuture<ByteBuffer> completableFuture = new CompletableFuture<>();
        try
        {
            completableFuture.complete(ByteBuffer.wrap(Files.readAllBytes(file)));
        }
        catch (IOException e)
        {
            completableFuture.completeExceptionally(e);
            return Optional.of(completableFuture);
        }

        logger.debug("Using data from buffer file {} of size {} for {} {}", file, size, serverDirection, requestId);
        return Optional.of(completableFuture);
    }
}