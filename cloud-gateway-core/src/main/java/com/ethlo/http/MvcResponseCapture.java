package com.ethlo.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import org.jetbrains.annotations.NotNull;

import com.ethlo.chronograph.Chronograph;
import com.ethlo.http.netty.ServerDirection;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

public class MvcResponseCapture extends HttpServletResponseWrapper
{
    private final Chronograph chronograph;
    private final String requestId;
    private final DataBufferRepository repository;
    private LoggingOutputStream loggingStream;
    private PrintWriter writer;

    public MvcResponseCapture(final Chronograph chronograph, HttpServletResponse response, String requestId, DataBufferRepository repository)
    {
        super(response);
        this.chronograph = chronograph;
        this.requestId = requestId;
        this.repository = repository;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException
    {
        if (writer != null)
        {
            throw new IllegalStateException("getWriter() has already been called on this response.");
        }
        if (loggingStream == null)
        {
            loggingStream = new LoggingOutputStream(super.getOutputStream());
        }
        return loggingStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException
    {
        if (loggingStream != null)
        {
            throw new IllegalStateException("getOutputStream() has already been called on this response.");
        }
        if (writer == null)
        {
            loggingStream = new LoggingOutputStream(super.getOutputStream());
            writer = new PrintWriter(new OutputStreamWriter(loggingStream, getCharacterEncoding()));
        }
        return writer;
    }

    public void copyBodyToResponse()
    {
        chronograph.time("response_body_flush", () ->
                {
                    if (writer != null)
                    {
                        writer.flush();
                    }
                    else if (loggingStream != null)
                    {
                        try
                        {
                            loggingStream.flush();
                        }
                        catch (IOException e)
                        {
                            throw new UncheckedIOException(e);
                        }
                    }
                }
        );
    }


    private class LoggingOutputStream extends ServletOutputStream
    {
        private final ServletOutputStream delegate;

        public LoggingOutputStream(ServletOutputStream delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void flush() throws IOException
        {
            delegate.flush();
        }

        @Override
        public void write(int b) throws IOException
        {
            delegate.write(b);
            chronograph.time("response_body_capture", () ->
                    repository.writeSync(ServerDirection.RESPONSE, requestId, ByteBuffer.wrap(new byte[]{(byte) b}))
            );
        }

        @Override
        public void write(@NotNull byte[] b, int off, int len) throws IOException
        {
            delegate.write(b, off, len);
            if (len > 0)
            {
                chronograph.time("response_body_capture", () ->
                        repository.writeSync(ServerDirection.RESPONSE, requestId, ByteBuffer.wrap(b, off, len))
                );
            }
        }

        @Override
        public boolean isReady()
        {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener)
        {
            delegate.setWriteListener(writeListener);
        }
    }
}