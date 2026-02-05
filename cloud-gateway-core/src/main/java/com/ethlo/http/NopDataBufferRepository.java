package com.ethlo.http;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.netty.ServerDirection;

public class NopDataBufferRepository implements DataBufferRepository
{
    public static final DataBufferRepository INSTANCE = new NopDataBufferRepository();

    private NopDataBufferRepository()
    {

    }

    @Override
    public void writeHeaders(final ServerDirection direction, final String requestId, final HttpHeaders headers)
    {

    }

    @Override
    public Optional<HttpHeaders> readHeaders(final ServerDirection direction, final String requestId)
    {
        return Optional.empty();
    }

    @Override
    public void writeBody(final ServerDirection direction, final String requestId, final ByteBuffer data)
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
    public Optional<BodyProvider> getBody(final ServerDirection serverDirection, final String requestId, @Nullable final String contentEncoding)
    {
        return Optional.empty();
    }

    @Override
    public void archive(final String requestId, final Path archiveDir)
    {

    }
}
