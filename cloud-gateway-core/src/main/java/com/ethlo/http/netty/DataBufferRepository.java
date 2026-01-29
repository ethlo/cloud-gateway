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
import org.springframework.data.util.Pair;

import ch.qos.logback.core.util.CloseUtil;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.BodyProvider;

public class DataBufferRepository
{
    private final Path basePath;
    private final ConcurrentMap<Path, FileHandle> pool = new ConcurrentHashMap<>();

    public DataBufferRepository(CaptureConfiguration config) throws IOException
    {
        this.basePath = Files.createDirectories(config.getLogDirectory());
    }

    public int writeSync(ServerDirection direction, String requestId, ByteBuffer data)
    {
        final Path path = getPath(direction, requestId);
        final FileHandle handle = getHandle(path);

        final int bytesToWrite = data.remaining();
        try
        {
            // Lock-free position calculation
            final long writeAt = handle.position().getAndAdd(bytesToWrite);

            // Thread-safe positioned write
            return handle.channel().write(data, writeAt);
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
        // Atomically remove the entire handle (Channel + Position)
        final FileHandle handle = pool.remove(path);
        if (handle != null)
        {
            CloseUtil.closeQuietly(handle.channel());
        }
    }

    public void cleanup(String requestId)
    {
        close(requestId);
        delete(getPath(ServerDirection.REQUEST, requestId));
        delete(getPath(ServerDirection.RESPONSE, requestId));
    }

    private void delete(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }

    private Path getPath(ServerDirection dir, String id)
    {
        return basePath.resolve(id + "_" + dir.name().toLowerCase() + ".raw");
    }

    public Pair<@NonNull String, @NonNull String> getBufferFileNames(String requestId)
    {
        return Pair.of(getPath(ServerDirection.REQUEST, requestId).getFileName().toString(),
                getPath(ServerDirection.RESPONSE, requestId).getFileName().toString()
        );
    }

    public Optional<BodyProvider> get(ServerDirection dir, String id)
    {
        final Path path = getPath(dir, id);
        // If it's in the pool, it means it's currently being written/active
        if (pool.containsKey(path))
        {
            try
            {
                // Return a fresh read-only channel for the logger
                return Optional.of(new BodyProvider(id, dir, path,
                        FileChannel.open(path, StandardOpenOption.READ)
                ));
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        return Optional.empty();
    }

    // Internal record to group the channel and its state
    private record FileHandle(FileChannel channel, AtomicLong position)
    {
    }
}