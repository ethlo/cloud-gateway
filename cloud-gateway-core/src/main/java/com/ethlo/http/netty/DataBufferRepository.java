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
    private final ConcurrentMap<Path, FileChannel> pool = new ConcurrentHashMap<>();

    public DataBufferRepository(CaptureConfiguration config) throws IOException
    {
        this.basePath = Files.createDirectories(config.getLogDirectory());
    }

    public int writeSync(ServerDirection direction, String requestId, ByteBuffer data)
    {
        final FileChannel fc = getChannel(direction, requestId);
        try
        {
            synchronized (fc)
            {
                return fc.write(data);
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private FileChannel getChannel(ServerDirection direction, String requestId)
    {
        final Path path = getPath(direction, requestId);
        return pool.computeIfAbsent(path, p -> {
                    try
                    {
                        return FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
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
        Optional.ofNullable(pool.remove(path)).ifPresent(CloseUtil::closeQuietly);
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

    public Pair<String, String> getBufferFileNames(String requestId)
    {
        return Pair.of(getPath(ServerDirection.REQUEST, requestId).getFileName().toString(),
                getPath(ServerDirection.RESPONSE, requestId).getFileName().toString()
        );
    }

    public Optional<RawProvider> get(ServerDirection dir, String id)
    {
        final Path path = getPath(dir, id);
        return Optional.ofNullable(pool.get(path)).map(fc ->
        {
            try
            {
                return new RawProvider(id, dir, path, FileChannel.open(path));
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        });
    }
}