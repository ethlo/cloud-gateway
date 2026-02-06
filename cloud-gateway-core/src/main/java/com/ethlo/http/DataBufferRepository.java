package com.ethlo.http;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.ServerDirection;

public interface DataBufferRepository
{
    void writeHeaders(ServerDirection direction, String requestId, HttpHeaders headers);

    Optional<HttpHeaders> readHeaders(final ServerDirection direction, final String requestId);

    void writeBody(ServerDirection direction, String requestId, ByteBuffer data);

    void persistForError(String requestId);

    void cleanup(String requestId);

    Optional<BodyProvider> getBody(ServerDirection serverDirection, String requestId, @Nullable String contentEncoding);

    void archive(WebExchangeDataProvider data, Path archiveDir);
}
