package com.ethlo.http.model;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.RequestPath;

import com.ethlo.http.netty.DataBufferRepository;

public class WebExchangeDataProvider
{
    private final DataBufferRepository dataBufferRepository;
    private String requestId;
    private Route route;
    private HttpMethod method;
    private RequestPath path;
    private URI uri;
    private HttpStatusCode statusCode;
    private HttpHeaders requestHeaders;
    private HttpHeaders responseHeaders;
    private OffsetDateTime timestamp;
    private Duration duration;
    private InetSocketAddress remoteAddress;

    public WebExchangeDataProvider(DataBufferRepository dataBufferRepository)
    {
        this.dataBufferRepository = dataBufferRepository;
    }

    public WebExchangeDataProvider requestId(String requestId)
    {
        this.requestId = requestId;
        return this;
    }

    public WebExchangeDataProvider method(HttpMethod method)
    {
        this.method = method;
        return this;
    }

    public WebExchangeDataProvider path(RequestPath path)
    {
        this.path = path;
        return this;
    }

    public WebExchangeDataProvider uri(URI uri)
    {
        this.uri = uri;
        return this;
    }

    public WebExchangeDataProvider statusCode(HttpStatusCode statusCode)
    {
        this.statusCode = statusCode;
        return this;
    }

    public WebExchangeDataProvider requestHeaders(HttpHeaders requestHeaders)
    {
        this.requestHeaders = requestHeaders;
        return this;
    }

    public WebExchangeDataProvider responseHeaders(HttpHeaders responseHeaders)
    {
        this.responseHeaders = responseHeaders;
        return this;
    }

    public WebExchangeDataProvider timestamp(OffsetDateTime timestamp)
    {
        this.timestamp = timestamp;
        return this;
    }

    public WebExchangeDataProvider duration(Duration duration)
    {
        this.duration = duration;
        return this;
    }

    public WebExchangeDataProvider remoteAddress(InetSocketAddress remoteAddress)
    {
        this.remoteAddress = remoteAddress;
        return this;
    }

    public Optional<PayloadProvider> getRequestPayload()
    {
        return dataBufferRepository.get(DataBufferRepository.Operation.REQUEST, requestId);
    }

    public Optional<PayloadProvider> getResponsePayload()
    {
        return dataBufferRepository.get(DataBufferRepository.Operation.RESPONSE, requestId);
    }

    public String getRequestId()
    {
        return requestId;
    }

    public HttpMethod getMethod()
    {
        return method;
    }

    public RequestPath getPath()
    {
        return path;
    }

    public URI getUri()
    {
        return uri;
    }

    public HttpStatusCode getStatusCode()
    {
        return statusCode;
    }

    public HttpHeaders getRequestHeaders()
    {
        return requestHeaders;
    }

    public HttpHeaders getResponseHeaders()
    {
        return responseHeaders;
    }

    public OffsetDateTime getTimestamp()
    {
        return timestamp;
    }

    public Duration getDuration()
    {
        return duration;
    }

    public InetSocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public Map<String, Object> asMetaMap()
    {
        final Map<String, Object> params = new HashMap<>();
        params.put("route_id", route.getId());
        params.put("route_uri", route.getUri());
        params.put("timestamp", getTimestamp());
        params.put("gateway_request_id", getRequestId());
        params.put("method", getMethod().name());
        params.put("path", getPath().value());
        params.put("duration", getDuration().toNanos());
        params.put("status", getStatusCode().value());
        params.put("is_error", getStatusCode().isError());
        params.put("request_headers", getRequestHeaders());
        params.put("response_headers", getResponseHeaders());
        return params;
    }

    public Route getRoute()
    {
        return route;
    }

    public WebExchangeDataProvider route(final Route route)
    {
        this.route = route;
        return this;
    }
}
