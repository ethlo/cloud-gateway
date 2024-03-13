package com.ethlo.http.netty;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.util.Optional;
import java.util.concurrent.Future;

import com.ethlo.http.model.RawProvider;

public interface DataBufferRepository
{
    void cleanup(String requestId);

    Future<Integer> write(final ServerDirection operation, final String id, ByteBuffer data);

    AsynchronousFileChannel getAsyncFileChannel(final ServerDirection operation, final String id);

    void close(String requestId);

    Optional<RawProvider> get(final ServerDirection serverDirection, final String id);

    void appendSizeAvailable(ServerDirection operation, String requestId, int byteCount);
}
