package com.ethlo.http.netty;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.unit.DataSize;

import ch.qos.logback.core.util.CloseUtil;

@Repository
public class PooledFileDataBufferRepository implements DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(PooledFileDataBufferRepository.class);
    private final DataSize bufferSize;
    private final Path basePath;
    private final ConcurrentMap<Path, OutputStream> pool;

    public PooledFileDataBufferRepository(@Value("${payload-logging.in-mem-buffer-size}") final DataSize bufferSize, @Value("${payload-logging.tmp-path}") final Path basePath)
    {
        this.bufferSize = bufferSize;
        this.basePath = basePath;
        this.pool = new ConcurrentHashMap<>();
    }

    @Override
    public void cleanup(final String requestId)
    {
        logger.debug("Deleting buffer files for {}", requestId);
        try
        {
            final Path requestFile = getFilename(Operation.REQUEST, requestId);
            final Path responseFile = getFilename(Operation.RESPONSE, requestId);
            if (pool.remove(requestFile) != null)
            {
                Files.delete(requestFile);
            }

            if (pool.remove(responseFile) != null)
            {
                Files.delete(responseFile);
            }
        }
        catch (NoSuchFileException exc)
        {
            logger.debug("File not found when attempting to delete: {}", exc.getFile());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void save(final Operation operation, final String requestId, final byte[] data)
    {
        final Path file = getFilename(operation, requestId);
        final OutputStream out = pool.compute(file, (f, outputStream) ->
        {
            if (outputStream == null)
            {
                outputStream = new BufferedOutputStream(new LazyFileOutputStream(f), Math.toIntExact(bufferSize.toBytes()));
                logger.debug("Opened buffer file for {} for {}", operation, requestId);
            }
            return outputStream;
        });
        try
        {
            out.write(data);
            logger.trace("Wrote {} bytes to buffer file for {} {}", data.length, operation, requestId);
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
        logger.debug("Closing buffer files for request {}", requestId);
        Optional.ofNullable(pool.get(getFilename(Operation.REQUEST, requestId))).ifPresent(CloseUtil::closeQuietly);
        Optional.ofNullable(pool.get(getFilename(Operation.RESPONSE, requestId))).ifPresent(CloseUtil::closeQuietly);
    }

    @Override
    public BufferedInputStream get(final Operation operation, final String id)
    {
        try
        {
            return new BufferedInputStream(Files.newInputStream(getFilename(operation, id)));
        }
        catch (NoSuchFileException exc)
        {
            return null;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
