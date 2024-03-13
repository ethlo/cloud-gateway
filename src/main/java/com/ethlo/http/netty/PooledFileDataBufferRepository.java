package com.ethlo.http.netty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.core.util.CloseUtil;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.RawProvider;

public class PooledFileDataBufferRepository implements DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(PooledFileDataBufferRepository.class);

    private final Path basePath;
    private final ConcurrentMap<Path, AsynchronousFileChannel> pool;
    private final ConcurrentMap<String, AtomicLong> sizePool;

    public PooledFileDataBufferRepository(CaptureConfiguration captureConfiguration)
    {
        this.basePath = captureConfiguration.getLogDirectory();
        this.pool = new ConcurrentHashMap<>();
        this.sizePool = new ConcurrentHashMap<>();
    }

    public static Path getFilename(final Path basePath, ServerDirection operation, String id)
    {
        return basePath.resolve(id + "_" + operation.name().toLowerCase() + ".raw");
    }

    @Override
    public void cleanup(final String requestId)
    {
        //close(requestId);

        logger.debug("Cleaning up buffer files for request {}", requestId);

        sizePool.remove(requestId + "_" + ServerDirection.REQUEST.name());
        cleanup(getFilename(basePath, ServerDirection.REQUEST, requestId));

        sizePool.remove(requestId + "_" + ServerDirection.RESPONSE.name());
        cleanup(getFilename(basePath, ServerDirection.RESPONSE, requestId));
    }

    private void cleanup(Path file)
    {
        Optional.ofNullable(pool.remove(file)).ifPresent(requestBuffer ->
        {
            logger.debug("Deleting buffer file {}", file);
            deleteSilently(file);
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
    public Future<Integer> write(final ServerDirection operation, final String requestId, final ByteBuffer data)
    {
        final AsynchronousFileChannel out = getAsyncFileChannel(operation, requestId);
        try
        {
            logger.trace("Writing data for {} for request {}", operation, requestId);
            return out.write(data, out.size());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public AsynchronousFileChannel getAsyncFileChannel(final ServerDirection operation, final String requestId)
    {
        final Path file = getFilename(basePath, operation, requestId);
        return pool.compute(file, (f, channel) ->
        {
            if (channel == null)
            {
                try
                {
                    channel = AsynchronousFileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
                    logger.debug("Opened buffer for {} for {}", operation, requestId);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }
            return channel;
        });
    }

    @Override
    public void close(final String requestId)
    {
        final Path requestFile = getFilename(basePath, ServerDirection.REQUEST, requestId);
        Optional.ofNullable(pool.get(requestFile)).ifPresent(fc ->
        {
            logger.debug("Closing request file {} used by request {}", requestFile, requestId);
            CloseUtil.closeQuietly(fc);
        });

        final Path responseFile = getFilename(basePath, ServerDirection.RESPONSE, requestId);
        Optional.ofNullable(pool.get(responseFile)).ifPresent(fc ->
        {
            logger.debug("Closing response file {} used by request {}", responseFile, requestId);
            CloseUtil.closeQuietly(fc);
        });
    }

    @Override
    public Optional<RawProvider> get(final ServerDirection serverDirection, final String requestId)
    {
        final String key = requestId + "_" + serverDirection.name();
        final Optional<Long> size = Optional.ofNullable(sizePool.get(key)).map(AtomicLong::get);

        if (size.isEmpty())
        {
            logger.debug("sizePool for {} is empty for request {}", serverDirection.name(), requestId);
            return Optional.empty();
        }

        final Path file = getFilename(basePath, serverDirection, requestId);
        return Optional.ofNullable(pool.get(file))
                .map(stream ->
                {
                    logger.debug("Using data from buffer file {} of size {} for {} {}", file, size.get(), serverDirection, requestId);
                    return Optional.of(new RawProvider(stream));
                }).orElse(Optional.empty());
    }

    @Override
    public void appendSizeAvailable(final ServerDirection operation, final String requestId, final int byteCount)
    {
        sizePool.compute(requestId + "_" + operation.name(), (reqId, size) ->
        {
            if (size == null)
            {
                size = new AtomicLong();
                logger.trace("Opened size calculation counter for {} for {}", operation, requestId);
            }
            final long newSize = size.addAndGet(byteCount);
            logger.trace("{} size: {}", operation, newSize);
            return size;
        });
    }
}
