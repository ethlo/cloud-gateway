package com.ethlo.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
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

    @NotNull
    private static HttpHeaders parseHttpHeaders(byte[] data)
    {
        final String content = new String(data, StandardCharsets.UTF_8);
        final HttpHeaders headers = new HttpHeaders();

        // Split by CRLF to get individual lines
        final String[] lines = content.split("\r\n");
        for (String line : lines)
        {
            if (line.isBlank())
            {
                continue;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex != -1)
            {
                final String name = line.substring(0, colonIndex).trim();
                final String value = line.substring(colonIndex + 1).trim();
                headers.add(name, value);
            }
        }
        return headers;
    }

    @Override
    public void putHeaders(ServerDirection direction, String requestId, HttpHeaders headers)
    {
        final Path path = getPath(direction, requestId, "headers");
        try (FileChannel fc = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        ))
        {
            writeFully(fc, ByteBuffer.wrap(serialize(headers)));
        }
        catch (IOException e)
        {
            logger.error("Failed to write headers {} for {}", direction, requestId, e);
        }
    }

    private byte[] serialize(HttpHeaders headers)
    {
        final StringBuilder sb = new StringBuilder(512);
        headers.forEach((name, values) ->
        {
            for (String value : values)
            {
                // We repeat the header name for multi-values (Standard Wire Format)
                // This is safer than comma-separation for things like Set-Cookie
                sb.append(name).append(": ").append(value).append("\r\n");
            }
        });
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<HttpHeaders> getHeaders(final ServerDirection direction, final String requestId)
    {
        final Path path = getPath(direction, requestId, "headers");

        if (!Files.exists(path))
        {
            return Optional.empty();
        }

        try
        {
            final byte[] data = Files.readAllBytes(path);
            final HttpHeaders headers = parseHttpHeaders(data);
            return Optional.of(headers);
        }
        catch (IOException e)
        {
            logger.error("Failed to read durable headers for {}", requestId, e);
            return Optional.empty();
        }
    }

    /**
     * Deterministic write logic. Checks threshold and spills to disk if necessary.
     */
    @Override
    public void writeBody(ServerDirection direction, String requestId, ByteBuffer data)
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
        Path path = getPath(dir, id, "body");
        FileChannel fc = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        int written = fc.write(data);
        return new DataState(null, fc, new AtomicLong(written));
    }

    private void writeFully(FileChannel fc, ByteBuffer src) throws IOException
    {
        writeFully(fc, src, null);
    }

    private void writeFully(FileChannel fc, ByteBuffer src, Long offset) throws IOException
    {
        while (src.hasRemaining())
        {
            // If offset is null, use the channel's current position (and update it)
            // If offset is provided, use absolute position (does NOT update channel position)
            int written = (offset == null) ? fc.write(src) : fc.write(src, offset);

            if (written == 0)
            {
                // Standard retry logic for edge cases/interrupts
                Thread.yield();
            }
            else if (offset != null)
            {
                offset += written;
            }
        }
    }

    private DataState spillExistingToDisk(ServerDirection dir, String id, ByteArrayOutputStream existing, ByteBuffer data) throws IOException
    {
        final Path path = getPath(dir, id, "body");
        final FileChannel fc = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        // Write the accumulated memory buffer fully
        writeFully(fc, ByteBuffer.wrap(existing.toByteArray()));

        // Write the new incoming chunk fully
        int dataSize = data.remaining();
        writeFully(fc, data);

        return new DataState(null, fc, new AtomicLong(existing.size() + dataSize));
    }

    private void writeToChannel(FileChannel fc, AtomicLong pos, ByteBuffer data) throws IOException
    {
        final int bytesToWrite = data.remaining();
        final long startOffset = pos.getAndAdd(bytesToWrite);
        writeFully(fc, data, startOffset);
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
                deleteAndReport(dir, requestId, "body", "Unable to clean up body file for {} for {}");
                if (logger.isDebugEnabled())
                {
                    logger.debug("Deleted {} disk buffer for {}. File size {}B", dir, requestId, state.position.get());
                }
            }
            catch (IOException exc)
            {
                logger.warn(exc.getMessage(), exc);
            }
        }

        deleteAndReport(dir, requestId, "headers", "Unable to clean up headers file for {} for {}");
    }

    private void deleteAndReport(ServerDirection dir, String requestId, String headers, String format)
    {
        try
        {
            if (!Files.deleteIfExists(getPath(dir, requestId, headers)))
            {
                logger.warn(format, dir.name().toLowerCase(), requestId);
            }
        }
        catch (IOException exc)
        {
            logger.warn("Error deleting transient file {}", requestId, exc);
        }
    }

    private String getPoolKey(ServerDirection dir, String id)
    {
        return id + "_" + dir.name();
    }

    private Path getPath(ServerDirection dir, String id, String suffix)
    {
        return basePath.resolve(id + "_" + dir.name().toLowerCase() + "." + suffix);
    }

    @Override
    public Optional<BodyProvider> getBody(ServerDirection dir, String id)
    {
        final String contentEncoding = getHeaders(dir, id)
                .orElseThrow(() -> new IllegalArgumentException("Missing headers for " + id))
                .getFirst(HttpHeaders.CONTENT_ENCODING);
        final DataState state = statePool.get(getPoolKey(dir, id));
        if (state == null) return Optional.empty();

        if (state.memoryBuffer != null)
        {
            return Optional.of(new BodyProvider(state.memoryBuffer.toByteArray(), contentEncoding));
        }
        return Optional.of(new BodyProvider(getPath(dir, id, "body"), contentEncoding));
    }

    public void archive(WebExchangeDataProvider data, final Path archiveDir)
    {
        final String requestId = data.getRequestId();
        archive(data, ServerDirection.REQUEST, archiveDir);
        archive(data, ServerDirection.RESPONSE, archiveDir);
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


    private void archive(WebExchangeDataProvider data, ServerDirection direction, Path archiveDir)
    {
        final String requestId = data.getRequestId();
        final Optional<HttpHeaders> headers = getHeaders(direction, requestId);
        headers.ifPresent(header -> archiveCombined(data, direction, header, getBody(direction, requestId).orElse(BodyProvider.NONE), archiveDir));

    }

    private void archiveCombined(WebExchangeDataProvider data, ServerDirection dir, HttpHeaders headers, BodyProvider bodyProvider, Path archiveDir)
    {
        final String requestId = data.getRequestId();
        final String fileName = requestId + "_" + dir.name().toLowerCase() + ".raw";
        final Path target = archiveDir.resolve(fileName);

        try (FileChannel out = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        ))
        {
            // HTTP line
            writeFully(out, ByteBuffer.wrap(buildStartLine(data, dir).getBytes(StandardCharsets.UTF_8)));

            // Headers
            byte[] headerBytes = serialize(headers);
            writeFully(out, ByteBuffer.wrap(headerBytes));
            writeFully(out, ByteBuffer.wrap("\r\n".getBytes(StandardCharsets.US_ASCII)));

            // Body (zero copy)
            if (bodyProvider.file() != null && Files.exists(bodyProvider.file()))
            {
                try (FileChannel in = FileChannel.open(bodyProvider.file(), StandardOpenOption.READ))
                {
                    // transferTo appends at 'out's current position
                    long totalTransferred = 0;
                    long size = in.size();
                    while (totalTransferred < size)
                    {
                        totalTransferred += in.transferTo(totalTransferred, size - totalTransferred, out);
                    }
                }
            }
            // Handle memory-based bodies if they exist
            else if (bodyProvider.bytes() != null)
            {
                writeFully(out, ByteBuffer.wrap(bodyProvider.bytes()));
            }

            logger.debug("Archived combined {} for {}", fileName, requestId);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to archive " + requestId, e);
        }
    }

    private String buildStartLine(WebExchangeDataProvider data, ServerDirection dir)
    {
        if (dir == ServerDirection.REQUEST)
        {
            return String.format("%s %s %s\r\n",
                    data.getMethod(),
                    data.getUri(),
                    data.getProtocol()
            );
        }
        else
        {
            return String.format("%s %d %s\r\n",
                    "HTTP/1.1",
                    data.getStatusCode().value(),
                    getReasonPhrase(data.getStatusCode().value())
            );
        }
    }

    private String getReasonPhrase(int code)
    {
        final HttpStatus status = HttpStatus.resolve(code);
        if (status != null)
        {
            return status.getReasonPhrase();
        }

        // Fallback for custom codes like 499
        return switch (code)
        {
            case 499 -> "Client Closed Request";
            default -> "Status " + code;
        };
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