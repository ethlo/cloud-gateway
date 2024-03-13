package com.ethlo.http.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.AsynchronousFileChannel;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import reactor.core.publisher.Flux;

public class RawProvider
{
    private static final DataBufferFactory factory = new DefaultDataBufferFactory(true);
    private final AsynchronousFileChannel fileChannel;
    private final long size;

    public RawProvider(AsynchronousFileChannel fileChannel)
    {
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

    public Flux<DataBuffer> asDataBuffer()
    {
        return DataBufferUtils.readAsynchronousFileChannel(() -> fileChannel, factory, Math.toIntExact(size));
    }
}