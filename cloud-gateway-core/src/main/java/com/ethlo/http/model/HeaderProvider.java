package com.ethlo.http.model;

import com.ethlo.http.DataBufferRepository;
import com.ethlo.http.netty.ServerDirection;

import org.springframework.http.HttpHeaders;

/**
 * Provides a unified way to access request/response headers,
 * regardless of whether they are in memory or need to be read from disk.
 */
public class HeaderProvider
{
    private final DataBufferRepository repository;
    private final String requestId;
    private final ServerDirection serverDirection;
    private HttpHeaders cache;

    public HeaderProvider(final DataBufferRepository repository, final String requestId, final ServerDirection serverDirection)
    {
        this.repository = repository;
        this.requestId = requestId;
        this.serverDirection = serverDirection;
    }

    public HttpHeaders getHeaders()
    {
        if (cache == null)
        {
            cache = repository.readHeaders(serverDirection, requestId).orElseGet(HttpHeaders::new);
        }
        return cache;
    }
}