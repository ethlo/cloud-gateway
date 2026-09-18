package com.ethlo.http.netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ethlo.http.logger.CaptureConfiguration;

class DataBufferRepositoryTest
{
    private static final String REQUEST_ID = "some-request-id";

    @TempDir
    private Path logDirectory;

    private DataBufferRepository dataBufferRepository;

    @BeforeEach
    void setUp() throws IOException
    {
        final CaptureConfiguration captureConfiguration = new CaptureConfiguration();
        captureConfiguration.setLogDirectory(logDirectory);
        dataBufferRepository = new DataBufferRepository(captureConfiguration);
    }

    @Test
    void writesAreAppendedInTheOrderTheyWereIssued()
    {
        final List<String> chunks = List.of("GET / HTTP/1.1\r\n", "Host: example.com\r\n", "\r\n", "the body");

        // The offset is claimed when the write is issued, so the order does not depend on completion order
        final List<CompletableFuture<Integer>> writes = chunks.stream()
                .map(chunk -> dataBufferRepository.write(ServerDirection.REQUEST, REQUEST_ID, buffer(chunk)))
                .toList();
        writes.forEach(CompletableFuture::join);

        assertThat(contents(ServerDirection.REQUEST)).isEqualTo(String.join("", chunks));
    }

    @Test
    void writesLargerThanASinglePageAreStoredInFull()
    {
        final String payload = "x".repeat(512 * 1024);

        dataBufferRepository.write(ServerDirection.RESPONSE, REQUEST_ID, buffer(payload)).join();

        assertThat(contents(ServerDirection.RESPONSE)).isEqualTo(payload);
    }

    @Test
    void shortWritesAreFollowedUpUntilTheBufferIsExhausted()
    {
        final String payload = "abcdefghij";
        final ShortWriteChannel channel = new ShortWriteChannel(payload.length(), 3, true);
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        final ByteBuffer data = buffer(payload);

        dataBufferRepository.writeFully(channel, data, 0, data.position(), data.remaining(), 0, result);

        assertThat(result.join()).isEqualTo(payload.length());
        assertThat(channel.written()).isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
        assertThat(channel.writeCount()).isEqualTo(4);
    }

    @Test
    void shortWritesAreFollowedUpWhenTheChannelDoesNotAdvanceTheBuffer()
    {
        final String payload = "abcdefghij";
        final ShortWriteChannel channel = new ShortWriteChannel(payload.length(), 4, false);
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        final ByteBuffer data = buffer(payload);

        dataBufferRepository.writeFully(channel, data, 0, data.position(), data.remaining(), 0, result);

        assertThat(result.join()).isEqualTo(payload.length());
        assertThat(channel.written()).isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aChannelThatStopsMakingProgressFailsTheWrite()
    {
        final ShortWriteChannel channel = new ShortWriteChannel(10, 0, true);
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        final ByteBuffer data = buffer("abcdefghij");

        dataBufferRepository.writeFully(channel, data, 0, data.position(), data.remaining(), 0, result);

        assertThat(result).isCompletedExceptionally();
    }

    @Test
    void orphanedFilesOlderThanTheRetentionPeriodAreDeleted()
    {
        dataBufferRepository.write(ServerDirection.REQUEST, REQUEST_ID, buffer("left behind")).join();
        dataBufferRepository.close(REQUEST_ID);
        final Path file = DataBufferRepository.getFilename(logDirectory, ServerDirection.REQUEST, REQUEST_ID);
        backdate(file, Duration.ofHours(2));

        assertThat(dataBufferRepository.deleteOrphaned(Duration.ofHours(1))).containsExactly(file);
        assertThat(file).doesNotExist();
    }

    @Test
    void filesWithinTheRetentionPeriodAreKept()
    {
        dataBufferRepository.write(ServerDirection.REQUEST, REQUEST_ID, buffer("just written")).join();
        dataBufferRepository.close(REQUEST_ID);
        final Path file = DataBufferRepository.getFilename(logDirectory, ServerDirection.REQUEST, REQUEST_ID);

        assertThat(dataBufferRepository.deleteOrphaned(Duration.ofHours(1))).isEmpty();
        assertThat(file).exists();
    }

    @Test
    void filesStillHeldByARequestAreKeptRegardlessOfAge()
    {
        // No close(), so the request is still considered in flight even though the file looks old
        dataBufferRepository.write(ServerDirection.RESPONSE, REQUEST_ID, buffer("still streaming")).join();
        final Path file = DataBufferRepository.getFilename(logDirectory, ServerDirection.RESPONSE, REQUEST_ID);
        backdate(file, Duration.ofHours(2));

        assertThat(dataBufferRepository.deleteOrphaned(Duration.ofHours(1))).isEmpty();
        assertThat(file).exists();
    }

    @Test
    void unrelatedFilesAreLeftAlone() throws IOException
    {
        final Path unrelated = Files.writeString(logDirectory.resolve("notes.txt"), "keep me");
        backdate(unrelated, Duration.ofHours(2));

        assertThat(dataBufferRepository.deleteOrphaned(Duration.ofHours(1))).isEmpty();
        assertThat(unrelated).exists();
    }

    private void backdate(final Path file, final Duration age)
    {
        try
        {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private ByteBuffer buffer(final String content)
    {
        return ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
    }

    private String contents(final ServerDirection serverDirection)
    {
        final Path file = DataBufferRepository.getFilename(logDirectory, serverDirection, REQUEST_ID);
        try
        {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
