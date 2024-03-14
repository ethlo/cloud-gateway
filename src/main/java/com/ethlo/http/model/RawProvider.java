package com.ethlo.http.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
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

    public RawProvider(String requestId, ServerDirection serverDirection, Path file, AsynchronousFileChannel fileChannel)
    {
        this.requestId = requestId;
        this.serverDirection = serverDirection;
        this.file = file;
        this.fileChannel = fileChannel;
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

    public CompletableFuture<ByteBuffer> getBuffer()
    {
        final CompletableFuture<ByteBuffer> completableFuture = new CompletableFuture<>();
        long fileSize;
        try
        {
            fileSize = fileChannel.size();
        }
        catch (IOException e)
        {
            completableFuture.completeExceptionally(e);
            return completableFuture;
        }

        final ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(fileSize));
        fileChannel.read(buffer, 0, null, new CompletionHandler<Integer, Void>()
        {
            @Override
            public void completed(Integer result, Void attachment)
            {
                completableFuture.complete(buffer);
            }

            @Override
            public void failed(Throwable exc, Void attachment)
            {
                completableFuture.completeExceptionally(exc);
            }
        });
        logger.debug("Using data from buffer file {} of size {} for {} {}", file, size, serverDirection, requestId);
        return completableFuture;
    }
}