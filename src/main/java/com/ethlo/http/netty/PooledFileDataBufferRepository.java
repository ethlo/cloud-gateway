package com.ethlo.http.netty;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import com.ethlo.http.util.InspectableBufferedOutputStream;
import com.ethlo.http.util.LazyFileOutputStream;

@Repository
public class PooledFileDataBufferRepository implements DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(PooledFileDataBufferRepository.class);
    private final DataSize bufferSize;
    private final Path basePath;
    private final ConcurrentMap<Path, InspectableBufferedOutputStream> pool;

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
        cleanup(getFilename(Operation.REQUEST, requestId));
        cleanup(getFilename(Operation.RESPONSE, requestId));
    }

    private void cleanup(Path file)
    {
        Optional.ofNullable(pool.remove(file)).ifPresent(requestBuffer ->
        {
            if (requestBuffer.isFlushedToUnderlyingStream())
            {
                deleteSilently(file);
            }
        });
    }

    private void deleteSilently(Path requestFile)
    {
        try
        {
            Files.deleteIfExists(requestFile);
        }
        catch (IOException e)
        {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void save(final Operation operation, final String requestId, final byte[] data)
    {
        final Path file = getFilename(operation, requestId);
        final InspectableBufferedOutputStream out = pool.compute(file, (f, outputStream) ->
        {
            if (outputStream == null)
            {
                outputStream = new InspectableBufferedOutputStream(new LazyFileOutputStream(f), Math.toIntExact(bufferSize.toBytes()));
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
        return Optional.ofNullable(pool.get(getFilename(operation, id)))
                .map(stream ->
                {
                    if (!stream.isFlushedToUnderlyingStream())
                    {
                        final byte[] data = stream.getBuffer();
                        logger.debug("Using data directly from memory for {} {} with size {} bytes", operation, id, data.length);
                        return new BufferedInputStream(new ByteArrayInputStream(data));
                    }
                    stream.forceFlush();
                    stream.forceClose();
                    return null;
                }).orElseGet(
                        () ->
                        {
                            try
                            {
                                final Path file = getFilename(operation, id);
                                if (logger.isDebugEnabled())
                                {
                                    logger.debug("Size of file {} {}", file, Files.size(file));
                                }
                                return new BufferedInputStream(Files.newInputStream(file));
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
                );
    }
}
