package com.ethlo.http;

import java.io.ByteArrayOutputStream;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.netty.ServerDirection;

public class DefaultDataBufferRepository implements DataBufferRepository
{
    private static final Logger logger = LoggerFactory.getLogger(DefaultDataBufferRepository.class);
    private final Path basePath;
    private final long thresholdBytes;

    // A single map to track the state (RAM or DISK) for every unique request/direction pair
    private final ConcurrentMap<String, DataState> statePool = new ConcurrentHashMap<>();

    public DefaultDataBufferRepository(CaptureConfiguration config, DataSize threshold) throws IOException
    {
        this.basePath = Files.createDirectories(config.getLogDirectory());
        this.thresholdBytes = threshold.toBytes();
    }

    /**
     * Deterministic write logic. Checks threshold and spills to disk if necessary.
     */
    @Override
    public void writeSync(ServerDirection direction, String requestId, ByteBuffer data)
    {
        final String key = getPoolKey(direction, requestId);
        final int bytesToWrite = data.remaining();

        statePool.compute(key, (k, state) -> {
                    try
                    {
                        // First write, initialize in memory
                        if (state == null)
                        {
                            if (bytesToWrite > thresholdBytes)
                            {
                                return spillNewToDisk(direction, requestId, data);
                            }
                            return createInMemoryState(data);
                        }

                        // Already on disk, write to channel
                        if (state.channel != null)
                        {
                            writeToChannel(state.channel, state.position, data);
                            return state;
                        }

                        // Currently in memory, check if this write triggers spill
                        if (state.memoryBuffer.size() + bytesToWrite > thresholdBytes)
                        {
                            return spillExistingToDisk(direction, requestId, state.memoryBuffer, data);
                        }
                        else
                        {
                            state.memoryBuffer.write(data.array(), data.position(), bytesToWrite);
                            state.position.addAndGet(bytesToWrite);
                            return state;
                        }
                    }
                    catch (IOException e)
                    {
                        throw new UncheckedIOException(e);
                    }
                }
        );
    }

    private DataState createInMemoryState(ByteBuffer data) throws IOException
    {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        outputStream.write(bytes);
        return new DataState(outputStream, null, new AtomicLong(bytes.length));
    }

    private DataState spillNewToDisk(ServerDirection dir, String id, ByteBuffer data) throws IOException
    {
        Path path = getPath(dir, id);
        FileChannel fc = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        int written = fc.write(data);
        return new DataState(null, fc, new AtomicLong(written));
    }

    private void writeFully(FileChannel fc, ByteBuffer src, long offset) throws IOException
    {
        int attempts = 0;
        while (src.hasRemaining())
        {
            final int written = (offset < 0) ? fc.write(src) : fc.write(src, offset);

            if (written == 0)
            {
                attempts++;
                if (attempts > 10)
                {
                    throw new IOException("Critical: Zero bytes written to disk after 10 attempts. Disk full or IO stall?");
                }
                Thread.yield(); // Give the OS a moment to flush buffers
            }
            else
            {
                if (offset >= 0)
                {
                    offset += written;
                }
                attempts = 0; // Reset on progress
            }
        }
    }

    private DataState spillExistingToDisk(ServerDirection dir, String id, ByteArrayOutputStream existing, ByteBuffer data) throws IOException
    {
        final Path path = getPath(dir, id);
        final FileChannel fc = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        // Write the accumulated memory buffer fully
        writeFully(fc, ByteBuffer.wrap(existing.toByteArray()), -1);

        // Write the new incoming chunk fully
        int dataSize = data.remaining();
        writeFully(fc, data, -1);

        return new DataState(null, fc, new AtomicLong(existing.size() + dataSize));
    }

    private void writeToChannel(FileChannel fc, AtomicLong pos, ByteBuffer data) throws IOException
    {
        final int bytesToWrite = data.remaining();
        // We calculate the start offset, but we must ensure we write exactly bytesToWrite
        long currentOffset = pos.getAndAdd(bytesToWrite);
        while (data.hasRemaining())
        {
            int written = fc.write(data, currentOffset);
            currentOffset += written;
        }
    }

    public void cleanup(String requestId)
    {
        logger.debug("Cleanup {}", requestId);
        cleanupDirection(ServerDirection.REQUEST, requestId);
        cleanupDirection(ServerDirection.RESPONSE, requestId);
    }

    private void cleanupDirection(ServerDirection dir, String requestId)
    {
        String key = getPoolKey(dir, requestId);
        DataState state = statePool.remove(key);
        if (state != null && state.channel != null)
        {
            try
            {
                state.channel.close();
                Files.deleteIfExists(getPath(dir, requestId));
                logger.debug("Deleted {} disk buffer for {}", dir, requestId);
            }
            catch (IOException ignored)
            {
            }
        }
    }

    private String getPoolKey(ServerDirection dir, String id)
    {
        return id + "_" + dir.name();
    }

    private Path getPath(ServerDirection dir, String id)
    {
        return basePath.resolve(id + "_" + dir.name().toLowerCase() + ".body");
    }

    public Optional<BodyProvider> get(ServerDirection dir, String id, String contentEncoding)
    {
        DataState state = statePool.get(getPoolKey(dir, id));
        if (state == null) return Optional.empty();

        if (state.memoryBuffer != null)
        {
            return Optional.of(new BodyProvider(state.memoryBuffer.toByteArray(), contentEncoding));
        }
        return Optional.of(new BodyProvider(getPath(dir, id), contentEncoding));
    }

    public void persistForError(String requestId)
    {
        persistDirection(ServerDirection.REQUEST, requestId);
        persistDirection(ServerDirection.RESPONSE, requestId);
    }

    private void persistDirection(ServerDirection dir, String requestId)
    {
        final String key = getPoolKey(dir, requestId);
        final DataState state = statePool.get(key);

        if (state != null && state.memoryBuffer() != null)
        {
            // It was in RAM, move it to disk so it survives a bit longer
            try
            {
                Path errorPath = basePath.resolve("error_" + requestId + "_" + dir.name().toLowerCase() + ".body");
                Files.write(errorPath, state.memoryBuffer().toByteArray());
                logger.error("Logger failed. Saved RAM buffer to {}", errorPath);
            }
            catch (IOException e)
            {
                logger.error("Double failure: Could not even save RAM buffer to disk!", e);
            }
        }
    }

    // Helper record to manage the toggle between RAM and Disk
    private record DataState(
            ByteArrayOutputStream memoryBuffer,
            FileChannel channel,
            AtomicLong position
    )
    {
    }
}