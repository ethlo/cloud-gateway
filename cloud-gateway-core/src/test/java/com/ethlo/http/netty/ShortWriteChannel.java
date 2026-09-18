package com.ethlo.http.netty;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.util.concurrent.Future;

/**
 * An {@link AsynchronousFileChannel} that writes at most a fixed number of bytes per call, which a real channel is
 * allowed to do. Only the callback flavour of write is supported.
 */
class ShortWriteChannel extends AsynchronousFileChannel
{
    private final byte[] contents;
    private final int maxBytesPerWrite;
    private final boolean advanceBufferPosition;

    private int writeCount;
    private int highWaterMark;

    ShortWriteChannel(final int capacity, final int maxBytesPerWrite, final boolean advanceBufferPosition)
    {
        this.contents = new byte[capacity];
        this.maxBytesPerWrite = maxBytesPerWrite;
        this.advanceBufferPosition = advanceBufferPosition;
    }

    @Override
    public <A> void write(final ByteBuffer src, final long position, final A attachment, final CompletionHandler<Integer, ? super A> handler)
    {
        writeCount++;
        final int count = Math.min(maxBytesPerWrite, src.remaining());
        for (int i = 0; i < count; i++)
        {
            contents[Math.toIntExact(position) + i] = src.get(src.position() + i);
        }
        if (advanceBufferPosition)
        {
            src.position(src.position() + count);
        }
        highWaterMark = Math.max(highWaterMark, Math.toIntExact(position) + count);
        handler.completed(count, attachment);
    }

    byte[] written()
    {
        final byte[] result = new byte[highWaterMark];
        System.arraycopy(contents, 0, result, 0, highWaterMark);
        return result;
    }

    int writeCount()
    {
        return writeCount;
    }

    @Override
    public long size()
    {
        return highWaterMark;
    }

    @Override
    public boolean isOpen()
    {
        return true;
    }

    @Override
    public void close() throws IOException
    {
        // Nothing to release
    }

    @Override
    public AsynchronousFileChannel truncate(final long size)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void force(final boolean metaData)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> void lock(final long position, final long size, final boolean shared, final A attachment, final CompletionHandler<FileLock, ? super A> handler)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<FileLock> lock(final long position, final long size, final boolean shared)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock tryLock(final long position, final long size, final boolean shared)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> void read(final ByteBuffer dst, final long position, final A attachment, final CompletionHandler<Integer, ? super A> handler)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Integer> read(final ByteBuffer dst, final long position)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Integer> write(final ByteBuffer src, final long position)
    {
        throw new UnsupportedOperationException();
    }
}
