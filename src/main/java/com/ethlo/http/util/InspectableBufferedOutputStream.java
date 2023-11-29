package com.ethlo.http.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import jakarta.annotation.Nonnull;

public class InspectableBufferedOutputStream extends BufferedOutputStream
{
    private boolean flushed;
    private long total;

    public InspectableBufferedOutputStream(LazyFileOutputStream lazyFileOutputStream, int bufferByteSize)
    {
        super(lazyFileOutputStream, bufferByteSize);
    }

    public byte[] getBuffer()
    {
        return Arrays.copyOf(buf, count);
    }

    public boolean isFlushedToUnderlyingStream()
    {
        return flushed;
    }

    public void forceFlush()
    {
        try
        {
            flushBuffer();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void flush()
    {
        // We want to avoid flushing to file if not needed
    }

    @Override
    public synchronized void write(@Nonnull byte[] b, int off, int len) throws IOException
    {
        if (len >= this.buf.length)
        {
            this.flushBuffer();
            this.out.write(b, off, len);
        }
        else
        {
            if (len > this.buf.length - this.count)
            {
                this.flushBuffer();
            }

            System.arraycopy(b, off, this.buf, this.count, len);
            this.count += len;
        }
        total += len;
    }

    private void flushBuffer() throws IOException
    {
        flushed = true;
        if (this.count > 0)
        {
            this.out.write(this.buf, 0, this.count);
            this.count = 0;
        }
    }

    @Override
    public void close()
    {
        // Suppress closing
    }

    public void forceClose()
    {
        try
        {
            super.close();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    public long getTotalBytesWritten()
    {
        return total;
    }
}
