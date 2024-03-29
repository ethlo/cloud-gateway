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
        cleanup(getFilename(basePath, REQUEST, requestId));
        cleanup(getFilename(basePath, RESPONSE, requestId));
    }

    private void cleanup(Path file)
    {
        Optional.ofNullable(pool.remove(file)).ifPresent(requestBuffer ->
        {
            if (logger.isDebugEnabled())
            {
                try
                {
                    logger.debug("Deleting buffer file {} with size of {} bytes", file, Files.size(file));
                }
                catch (IOException exc)
                {
                    logger.trace("Ignored: File size calculation failed", exc);
                }
            }

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

    public CompletableFuture<Integer> write(final ServerDirection operation, final String requestId, final ByteBuffer data)
    {
        final BufferHolder holder = getAsyncFileChannel(operation, requestId);
        final CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        long fileSize;
        try
        {
            fileSize = holder.fileChannel.size();
        }
        catch (IOException e)
        {
            completableFuture.completeExceptionally(e);
            return completableFuture;
        }

        holder.fileChannel.write(data, fileSize, null, new CompletionHandler<Integer, Void>()
        {
            @Override
            public void completed(Integer result, Void attachment)
            {
                completableFuture.complete(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment)
            {
                completableFuture.completeExceptionally(exc);
            }
        });
        return completableFuture;
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

    public void close(final String requestId)
    {
        final Path requestFile = getFilename(basePath, REQUEST, requestId);
        getFileChannel(requestFile)
                .ifPresent(fc ->
                {
                    if (fc.isOpen())
                    {
                        logger.debug("Closing request file {} used by request {}", requestFile, requestId);
                        CloseUtil.closeQuietly(fc);
                    }
                });

        final Path responseFile = getFilename(basePath, RESPONSE, requestId);
        getFileChannel(responseFile).ifPresent(fc ->
        {
            if (fc.isOpen())
            {
                logger.debug("Closing response file {} used by request {}", responseFile, requestId);
                CloseUtil.closeQuietly(fc);
            }
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
