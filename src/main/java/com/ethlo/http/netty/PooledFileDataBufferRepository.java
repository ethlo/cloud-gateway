package com.ethlo.http.netty;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

import ch.qos.logback.core.util.CloseUtil;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.PayloadProvider;
import com.ethlo.http.util.InspectableBufferedOutputStream;
import com.ethlo.http.util.LazyFileOutputStream;
import rawhttp.core.HttpMessage;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;

public class PooledFileDataBufferRepository implements DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(PooledFileDataBufferRepository.class);

    private static final RawHttp rawHttp = new RawHttp(getConfig());

    private static RawHttpOptions getConfig()
    {
        final RawHttpOptions.Builder b = RawHttpOptions.newBuilder();
        b.withHttpHeadersOptions().withMaxHeaderValueLength(Integer.MAX_VALUE).withMaxHeaderNameLength(255);
        return b.build();
    }

    private final DataSize bufferSize;
    private final Path basePath;
    private final ConcurrentMap<Path, InspectableBufferedOutputStream> pool;
    private final ConcurrentMap<String, AtomicLong> sizePool;

    public PooledFileDataBufferRepository(CaptureConfiguration captureConfiguration)
    {
        this.bufferSize = captureConfiguration.getMemoryBufferSize();
        this.basePath = captureConfiguration.getTempDirectory();
        this.pool = new ConcurrentHashMap<>();
        this.sizePool = new ConcurrentHashMap<>();
    }

    public static Path getFilename(final Path basePath, ServerDirection operation, String id)
    {
        return basePath.resolve(operation.name().toLowerCase() + "_" + id);
    }

    @Override
    public void cleanup(final String requestId)
    {
        sizePool.remove(requestId + "_" + ServerDirection.REQUEST.name());
        sizePool.remove(requestId + "_" + ServerDirection.RESPONSE.name());
        cleanup(getFilename(basePath, ServerDirection.REQUEST, requestId));
        cleanup(getFilename(basePath, ServerDirection.RESPONSE, requestId));
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
    public void write(final ServerDirection operation, final String requestId, final byte[] data)
    {
        final OutputStream out = getOutputStream(operation, requestId);
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
    public OutputStream getOutputStream(final ServerDirection operation, final String requestId)
    {
        final Path file = getFilename(basePath, operation, requestId);
        return pool.compute(file, (f, outputStream) ->
        {
            if (outputStream == null)
            {
                outputStream = new InspectableBufferedOutputStream(new LazyFileOutputStream(f), Math.toIntExact(bufferSize.toBytes()));
                logger.debug("Opened buffer for {} for {}", operation, requestId);
            }
            return outputStream;
        });
    }

    @Override
    public void finished(final String requestId)
    {
        Optional.ofNullable(pool.get(getFilename(basePath, ServerDirection.REQUEST, requestId))).ifPresent(CloseUtil::closeQuietly);
        Optional.ofNullable(pool.get(getFilename(basePath, ServerDirection.RESPONSE, requestId))).ifPresent(CloseUtil::closeQuietly);
    }

    @Override
    public Optional<PayloadProvider> get(final ServerDirection serverDirection, final String requestId)
    {
        final Path file = getFilename(basePath, serverDirection, requestId);
        return Optional.ofNullable(sizePool.get(requestId + "_" + serverDirection.name())).map(AtomicLong::get)
                .flatMap(size ->
                        Optional.of(Optional.ofNullable(pool.get(file))
                                .map(stream ->
                                {
                                    if (!stream.isFlushedToUnderlyingStream())
                                    {
                                        final byte[] data = stream.getBuffer();
                                        logger.debug("Using data directly from memory for {} {}", serverDirection, requestId);
                                        return extractBody(new ByteArrayInputStream(data), serverDirection == ServerDirection.REQUEST, stream.getTotalBytesWritten());
                                    }
                                    stream.forceFlush();
                                    stream.forceClose();
                                    return new PayloadProvider(InputStream.nullInputStream(), null, size);
                                }).orElseGet(
                                        () ->
                                        {
                                            try
                                            {
                                                final long fileSize = Files.size(file);
                                                logger.debug("Size of file {} is {} bytes", file, fileSize);
                                                return extractBody(new BufferedInputStream(Files.newInputStream(file)), serverDirection == ServerDirection.REQUEST, fileSize);
                                            }
                                            catch (NoSuchFileException exc)
                                            {
                                                return new PayloadProvider(InputStream.nullInputStream(), null, size);
                                            }
                                            catch (IOException e)
                                            {
                                                throw new UncheckedIOException(e);
                                            }
                                        }
                                )));
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

    private static PayloadProvider extractBody(final InputStream fullMessage, boolean isRequest, final long totalBytesWritten)
    {
        try
        {
            final HttpMessage message = isRequest ? rawHttp.parseRequest(fullMessage) : rawHttp.parseResponse(fullMessage);
            final byte[] bodyBytes = message.getBody().map(b ->
            {
                try
                {
                    return b.decodeBody();
                }
                catch (IOException exc)
                {
                    throw new UncheckedIOException(exc);
                }
            }).orElse(new byte[0]);
            return new PayloadProvider(new ByteArrayInputStream(bodyBytes), (long) bodyBytes.length, totalBytesWritten);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }
}
