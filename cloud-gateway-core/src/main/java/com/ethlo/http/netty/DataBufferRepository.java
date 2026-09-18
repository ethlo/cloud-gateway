package com.ethlo.http.netty;

import static com.ethlo.http.netty.ServerDirection.REQUEST;
import static com.ethlo.http.netty.ServerDirection.RESPONSE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;

import ch.qos.logback.core.util.CloseUtil;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.RawProvider;

public class DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(DataBufferRepository.class);

    private final Path basePath;
    private final ConcurrentMap<Path, BufferHolder> pool;

    public DataBufferRepository(CaptureConfiguration captureConfiguration) throws IOException
    {
        this.basePath = Files.createDirectories(captureConfiguration.getLogDirectory());
        this.pool = new ConcurrentHashMap<>();
    }

    public static Path getFilename(final Path basePath, ServerDirection operation, String id)
    {
        return basePath.resolve(id + "_" + operation.name().toLowerCase() + ".raw");
    }

    public void cleanup(final String requestId)
    {
        close(requestId);

        logger.debug("Cleaning up buffer files for request {}", requestId);
        deleteSilently(getFilename(basePath, REQUEST, requestId));
        deleteSilently(getFilename(basePath, RESPONSE, requestId));
    }

    private void deleteSilently(Path requestFile)
    {
        if (logger.isDebugEnabled() && Files.exists(requestFile))
        {
            try
            {
                logger.debug("Deleting buffer file {} with size of {} bytes", requestFile, Files.size(requestFile));
            }
            catch (IOException exc)
            {
                logger.trace("Ignored: File size calculation failed", exc);
            }
        }

        try
        {
            Files.deleteIfExists(requestFile);
        }
        catch (IOException e)
        {
            logger.warn(e.getMessage(), e);
        }
    }

    /**
     * Appends the data to the buffer file of the request. The target offset is claimed synchronously, so writes are
     * appended in the order this method was called, rather than in the order they happen to complete.
     * <p>
     * Note that the buffer must not be modified or freed, and the file must not be read back, until the returned
     * future completes.
     */
    public CompletableFuture<Integer> write(final ServerDirection operation, final String requestId, final ByteBuffer data)
    {
        final BufferHolder holder = getAsyncFileChannel(operation, requestId);
        final CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        final int length = data.remaining();
        final long offset = holder.size.getAndAdd(length);
        writeFully(holder.fileChannel, data, offset, data.position(), length, 0, completableFuture);
        return completableFuture;
    }

    /**
     * A single write is not guaranteed to consume the whole buffer. As the offset was claimed for the full length,
     * a short write must be followed up rather than leaving the tail unwritten and a gap in the file.
     */
    void writeFully(final AsynchronousFileChannel fileChannel, final ByteBuffer data, final long offset, final int startPosition, final int length, final int writtenSoFar, final CompletableFuture<Integer> result)
    {
        if (writtenSoFar >= length)
        {
            result.complete(writtenSoFar);
            return;
        }

        // Set explicitly rather than relying on the channel to have advanced the buffer for us
        data.position(startPosition + writtenSoFar);

        fileChannel.write(data, offset + writtenSoFar, null, new CompletionHandler<Integer, Void>()
        {
            @Override
            public void completed(Integer written, Void attachment)
            {
                if (written <= 0 && writtenSoFar + written < length)
                {
                    result.completeExceptionally(new IOException("Wrote " + written + " bytes with " + (length - writtenSoFar) + " bytes remaining, giving up"));
                    return;
                }
                writeFully(fileChannel, data, offset, startPosition, length, writtenSoFar + written, result);
            }

            @Override
            public void failed(Throwable exc, Void attachment)
            {
                result.completeExceptionally(exc);
            }
        });
    }

    private BufferHolder getAsyncFileChannel(final ServerDirection serverDirection, final String requestId)
    {
        final Path file = getFilename(basePath, serverDirection, requestId);
        return pool.compute(file, (f, holder) ->
        {
            if (holder == null)
            {
                try
                {
                    holder = new BufferHolder(new AtomicLong(0), AsynchronousFileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.READ));
                    logger.debug("Opened buffer for {} for {}", serverDirection, requestId);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }
            return holder;
        });
    }

    /**
     * Closes any open channel for the request and releases the pooled entries. Note that this always removes the
     * entries from the pool, as the pool would otherwise grow unbounded for requests that are never cleaned up.
     */
    public void close(final String requestId)
    {
        release(getFilename(basePath, REQUEST, requestId), REQUEST, requestId);
        release(getFilename(basePath, RESPONSE, requestId), RESPONSE, requestId);
    }

    private void release(final Path file, final ServerDirection serverDirection, final String requestId)
    {
        Optional.ofNullable(pool.remove(file))
                .map(BufferHolder::fileChannel)
                .filter(AsynchronousFileChannel::isOpen)
                .ifPresent(fc ->
                {
                    logger.debug("Closing {} file {} used by request {}", serverDirection.name().toLowerCase(), file, requestId);
                    CloseUtil.closeQuietly(fc);
                });
    }

    private Optional<AsynchronousFileChannel> getFileChannel(Path file)
    {
        return Optional.ofNullable(pool.get(file))
                .filter(bufferHolder -> bufferHolder.fileChannel != null)
                .map(bufferHolder -> bufferHolder.fileChannel);
    }

    public Optional<RawProvider> get(final ServerDirection serverDirection, final String requestId)
    {
        final Path file = getFilename(basePath, serverDirection, requestId);
        return getFileChannel(file).map(fc -> new RawProvider(requestId, serverDirection, file, fc));
    }

    public void appendSizeAvailable(final ServerDirection serverDirection, final String requestId, final int byteCount)
    {
        final Path key = getFilename(basePath, serverDirection, requestId);
        pool.compute(key, (reqId, holder) ->
        {
            if (holder == null)
            {
                holder = new BufferHolder(new AtomicLong(), null);
                logger.debug("Opened size calculation counter for {} for {}", serverDirection, requestId);
            }
            final long newSize = holder.size.addAndGet(byteCount);
            logger.debug("{} size: {}", serverDirection, newSize);
            return holder;
        });
    }

    public Pair<String, String> getBufferFileNames(final String requestId)
    {
        return Pair.of(getFilename(basePath, REQUEST, requestId).getFileName().toString(), getFilename(basePath, RESPONSE, requestId).getFileName().toString());
    }

    private record BufferHolder(AtomicLong size, AsynchronousFileChannel fileChannel)
    {

    }
}
