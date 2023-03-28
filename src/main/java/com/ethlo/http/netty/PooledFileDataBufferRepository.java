package com.ethlo.http.netty;

import static com.ethlo.http.util.HttpMessageUtil.findBodyPositionInStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.unit.DataSize;

import ch.qos.logback.core.util.CloseUtil;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.PayloadProvider;
import com.ethlo.http.util.InspectableBufferedOutputStream;
import com.ethlo.http.util.LazyFileOutputStream;

@Repository
public class PooledFileDataBufferRepository implements DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(PooledFileDataBufferRepository.class);
    private final DataSize bufferSize;
    private final Path basePath;
    private final ConcurrentMap<Path, InspectableBufferedOutputStream> pool;

    public PooledFileDataBufferRepository(CaptureConfiguration captureConfiguration)
    {
        this.bufferSize = captureConfiguration.getMemoryBufferSize();
        this.basePath = captureConfiguration.getTempDirectory();
        this.pool = new ConcurrentHashMap<>();
    }

    public static Path getFilename(final Path basePath, Operation operation, String id)
    {
        return basePath.resolve(operation.name().toLowerCase() + "_" + id);
    }

    @Override
    public void cleanup(final String requestId)
    {
        cleanup(getFilename(basePath, Operation.REQUEST, requestId));
        cleanup(getFilename(basePath, Operation.RESPONSE, requestId));
    }

    private void cleanup(Path file)
    {
        Optional.ofNullable(pool.remove(file)).ifPresent(requestBuffer ->
        {
            if (requestBuffer.isFlushedToUnderlyingStream())
            {
                logger.debug("Deleting buffer file {}", file);
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
        final Path file = getFilename(basePath, operation, requestId);
        final InspectableBufferedOutputStream out = pool.compute(file, (f, outputStream) ->
        {
            if (outputStream == null)
            {
                outputStream = new InspectableBufferedOutputStream(new LazyFileOutputStream(f), Math.toIntExact(bufferSize.toBytes()));
                logger.debug("Opened buffer for {} for {}", operation, requestId);
            }
            return outputStream;
        });
        try
        {
            out.write(data);
            logger.trace("Wrote {} bytes to buffer for {} {}", data.length, operation, requestId);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void finished(final String requestId)
    {
        Optional.ofNullable(pool.get(getFilename(basePath, Operation.REQUEST, requestId))).ifPresent(CloseUtil::closeQuietly);
        Optional.ofNullable(pool.get(getFilename(basePath, Operation.RESPONSE, requestId))).ifPresent(CloseUtil::closeQuietly);
    }

    @Override
    public Optional<PayloadProvider> get(final Operation operation, final String id)
    {
        final Path file = getFilename(basePath, operation, id);
        return Optional.ofNullable(Optional.ofNullable(pool.get(file))
                .map(stream ->
                {
                    if (!stream.isFlushedToUnderlyingStream())
                    {
                        final byte[] data = stream.getBuffer();
                        logger.debug("Using data directly from memory for {} {}", operation, id);
                        final InputStream in = new BufferedInputStream(new ByteArrayInputStream(data));
                        final long skipped = pos(in);
                        return new PayloadProvider(in, data.length - skipped);
                    }
                    stream.forceFlush();
                    stream.forceClose();
                    return null;
                }).orElseGet(
                        () ->
                        {
                            try
                            {
                                if (logger.isDebugEnabled())
                                {
                                    logger.debug("Size of file {} is {} bytes", file, Files.size(file));
                                }
                                final InputStream in = new BufferedInputStream(Files.newInputStream(file));
                                final long skipped = pos(in);
                                return new PayloadProvider(in, Files.size(file) - skipped);
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
                ));
    }

    private long pos(InputStream rawData)
    {
        if (rawData != null)
        {
            try
            {
                return findBodyPositionInStream(rawData);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        return 0;
    }
}
