package com.ethlo.http.netty;

import static java.nio.file.Files.delete;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import ch.qos.logback.core.util.CloseUtil;

@Repository
public class PooledFileDataBufferRepository implements DataBufferRepository
{
    private final Path basePath;
    private final ConcurrentMap<Path, OutputStream> pool;

    public PooledFileDataBufferRepository(@Value("${payload-logging.tmp-path}") final Path basePath)
    {
        this.basePath = basePath;
        this.pool = new ConcurrentHashMap<>();
    }

    @Override
    public void cleanup(final String requestId)
    {
        try
        {
            delete(getFilename(Operation.REQUEST, requestId));
            delete(getFilename(Operation.RESPONSE, requestId));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void save(final Operation operation, final String id, final byte[] data)
    {
        final Path file = getFilename(operation, id);
        final OutputStream out = pool.compute(file, (f, outputStream) ->
        {
            if (outputStream == null)
            {
                try
                {
                    outputStream = Files.newOutputStream(f);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }
            return outputStream;
        });
        try
        {
            out.write(data);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private Path getFilename(Operation operation, String id)
    {
        return basePath.resolve(operation.name().toLowerCase() + "_" + id);
    }

    @Override
    public void finished(final String requestId)
    {
        final Path fileRequest = getFilename(Operation.REQUEST, requestId);
        Optional.ofNullable(pool.remove(fileRequest)).ifPresent(CloseUtil::closeQuietly);

        final Path fileResponse = getFilename(Operation.RESPONSE, requestId);
        Optional.ofNullable(pool.remove(fileResponse)).ifPresent(CloseUtil::closeQuietly);
    }

    @Override
    public BufferedInputStream get(final Operation operation, final String id)
    {
        try
        {
            return new BufferedInputStream(Files.newInputStream(getFilename(operation, id)));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
