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
            logger.debug("Closing channel for {}: {} bytes", path, handle.position().get());
            CloseUtil.closeQuietly(handle.channel());
        }
    }

    public void cleanup(String requestId)
    {
        logger.debug("Cleanup {}", requestId);
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