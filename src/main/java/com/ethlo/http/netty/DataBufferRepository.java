package com.ethlo.http.netty;

import java.nio.channels.AsynchronousFileChannel;
import java.util.Optional;
import java.util.concurrent.Future;

import org.springframework.data.util.Pair;

import com.ethlo.http.model.RawProvider;

public interface DataBufferRepository
{
    void cleanup(String requestId);

    Future<Integer> write(final ServerDirection operation, final String id, byte[] data);

    AsynchronousFileChannel getAsyncFileChannel(final ServerDirection operation, final String id);

    void close(String requestId);

    Optional<RawProvider> get(final ServerDirection serverDirection, final String id);

    void appendSizeAvailable(ServerDirection operation, String requestId, int byteCount);

    Pair<String, String> getBufferFileNames(String requestId);
}
