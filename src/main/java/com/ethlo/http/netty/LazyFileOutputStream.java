package com.ethlo.http.netty;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LazyFileOutputStream extends OutputStream
{
    private final Path file;

    private OutputStream outputStream;
    private boolean opened = false;
    private boolean closed = false;


    public LazyFileOutputStream(Path file)
    {
        this.file = file;
    }

    @Override
    public void close() throws IOException
    {
        if (opened)
        {
            outputStream.flush();
            outputStream.close();
        }
        closed = true;
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int offset, int len) throws IOException
    {
        ensureOpen();
        outputStream.write(b, offset, len);
    }

    @Override
    public void write(int b) throws IOException
    {
        ensureOpen();
        outputStream.write(b);
    }

    private void ensureOpen() throws IOException
    {
        if (closed)
        {
            throw new IOException(file + " has already been closed.");
        }

        if (!opened)
        {
            outputStream = Files.newOutputStream(file, StandardOpenOption.CREATE);
            opened = true;
        }
    }
}