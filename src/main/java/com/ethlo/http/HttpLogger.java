package com.ethlo.http;

import java.io.InputStream;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

public interface HttpLogger
{
    void terminated(final ServerHttpRequest request, final ServerHttpResponse response, final InputStream inputStream, final InputStream stream);
}
