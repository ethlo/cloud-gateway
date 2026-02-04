package com.ethlo.http;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.netty.ServerDirection;

public interface DataBufferRepository
{
    DataBufferRepository NOP = new DataBufferRepository()
    {
        @Override
        public void writeSync(final ServerDirection direction, final String requestId, final ByteBuffer data)
        {

        }

        @Override
        public void persistForError(final String requestId)
        {

        }

        @Override
        public void cleanup(final String requestId)
        {

        }

        @Override
        public Optional<BodyProvider> get(final ServerDirection serverDirection, final String requestId, @Nullable final String contentEncoding)
        {
            return Optional.empty();
        }
    };

    void writeSync(ServerDirection direction, String requestId, ByteBuffer data);

    void persistForError(String requestId);

    void cleanup(String requestId);

    Optional<BodyProvider> get(ServerDirection serverDirection, String requestId, @Nullable String contentEncoding);
}
