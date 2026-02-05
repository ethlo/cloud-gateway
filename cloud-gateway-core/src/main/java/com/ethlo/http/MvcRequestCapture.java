package com.ethlo.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import org.jetbrains.annotations.NotNull;

import com.ethlo.chronograph.Chronograph;
import com.ethlo.http.netty.ServerDirection;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class MvcRequestCapture extends HttpServletRequestWrapper
{
    private final Chronograph chronograph;
    private final String requestId;
    private final DataBufferRepository repository;
    private ServletInputStream inputStream;

    public MvcRequestCapture(Chronograph chronograph, HttpServletRequest request, String requestId, DataBufferRepository repository)
    {
        super(request);
        this.chronograph = chronograph;
        this.requestId = requestId;
        this.repository = repository;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        if (inputStream == null)
        {
            inputStream = new LoggingInputStream(super.getInputStream());
        }
        return inputStream;
    }

    private class LoggingInputStream extends ServletInputStream
    {
        private final ServletInputStream delegate;

        public LoggingInputStream(ServletInputStream delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void close()
        {
            chronograph.time("close", () ->
                    {
                        try
                        {
                            delegate.close();
                        }
                        catch (IOException e)
                        {
                            throw new UncheckedIOException(e);
                        }
                    }
            );
        }

        @Override
        public int read() throws IOException
        {
            int b = delegate.read();
            if (b != -1)
            {
                chronograph.time("request_body_capture", () -> repository.writeBody(ServerDirection.REQUEST, requestId, ByteBuffer.wrap(new byte[]{(byte) b})));
            }
            return b;
        }

        @Override
        public int read(@NotNull byte[] b, int off, int len) throws IOException
        {
            int read = delegate.read(b, off, len);
            if (read > 0)
            {
                chronograph.time("request_body_capture", () -> repository.writeBody(ServerDirection.REQUEST, requestId, ByteBuffer.wrap(b, off, read)));
            }
            return read;
        }

        @Override
        public boolean isFinished()
        {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady()
        {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener)
        {
            delegate.setReadListener(readListener);
        }
    }
}