package com.ethlo.http.netty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;

import ch.qos.logback.core.util.CloseUtil;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.BodyProvider;

public class DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(DataBufferRepository.class);
    private final Path basePath;
    private final ConcurrentMap<Path, FileHandle> pool = new ConcurrentHashMap<>();

    public DataBufferRepository(CaptureConfiguration config) throws IOException
    {
        this.basePath = Files.createDirectories(config.getLogDirectory());
    }

    public void markComplete(ServerDirection direction, String requestId)
    {
        final Path path = getPath(direction, requestId);
        final FileHandle handle = pool.get(path);
        if (handle != null)
        {
            try
            {
                logger.debug("Closing channel for {} {}: {} bytes", requestId, direction, handle.position().get());
                handle.channel().close();
            }
            catch (IOException e)
            {
                logger.warn("Error closing channel for {} {}", requestId, direction, e);
            }
        }
    }

    public int writeSync(ServerDirection direction, String requestId, ByteBuffer data)
    {
        final Path path = getPath(direction, requestId);
        final FileHandle handle = getHandle(path);
        final int bytesToWrite = data.remaining();
        try
        {
            // Reserve the space for this chunk atomically
            final long startOffset = handle.position().getAndAdd(bytesToWrite);

            // Positional write is thread-safe and doesn't change the channel's position property
            int totalWritten = 0;
            while (totalWritten < bytesToWrite)
            {
                totalWritten += handle.channel().write(data, startOffset + totalWritten);
            }
            return totalWritten;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private FileHandle getHandle(Path path)
    {
        return pool.computeIfAbsent(path, p -> {
                    try
                    {
                        final FileChannel fc = FileChannel.open(p,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE
                        );
                        return new FileHandle(fc, new AtomicLong(0));
                    }
                    catch (IOException e)
                    {
                        throw new UncheckedIOException(e);
                    }
                }
        );
    }

    public void close(String requestId)
    {
        closeFile(getPath(ServerDirection.REQUEST, requestId));
        closeFile(getPath(ServerDirection.RESPONSE, requestId));
    }

    private void closeFile(Path path)
    {
        final FileHandle handle = pool.remove(path);
        if (handle != null)
        {
            CloseUtil.closeQuietly(handle.channel());
        }
    }

    public void cleanup(String requestId)
    {
        close(requestId);

        cleanupDirection(ServerDirection.REQUEST, requestId);
        cleanupDirection(ServerDirection.RESPONSE, requestId);
    }

    private void cleanupDirection(ServerDirection dir, String requestId)
    {
        final Path path = getPath(dir, requestId);
        if (delete(path))
        {
            logger.debug("Deleted {} file: {}", dir, path);
        }
    }

    private boolean delete(Path path)
    {
        try
        {
            return Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
        return false;
    }

    private Path getPath(ServerDirection dir, String id)
    {
        return basePath.resolve(id + "_" + dir.name().toLowerCase() + ".body");
    }

    public Pair<@NonNull String, @NonNull String> getBufferFileNames(String requestId)
    {
        return Pair.of(getPath(ServerDirection.REQUEST, requestId).getFileName().toString(),
                getPath(ServerDirection.RESPONSE, requestId).getFileName().toString()
        );
    }

    public Optional<BodyProvider> get(ServerDirection dir, String id, String contentEncoding)
    {
        final Path path = getPath(dir, id);
        if (Files.exists(path))
        {
            return Optional.of(new BodyProvider(path, contentEncoding));
        }
        return Optional.empty();
    }

    private record FileHandle(FileChannel channel, AtomicLong position)
    {
    }
}