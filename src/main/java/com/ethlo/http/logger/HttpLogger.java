package com.ethlo.http.logger;

import java.io.InputStream;
import java.util.Map;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import jakarta.annotation.Nullable;

public interface HttpLogger
{
    void completed(final ServerHttpRequest request, final ServerHttpResponse response, @Nullable final InputStream inputStream, @Nullable final InputStream stream);

    void accessLog(Map<String, Object> data);
}
