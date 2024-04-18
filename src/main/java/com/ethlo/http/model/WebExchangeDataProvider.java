package com.ethlo.http.model;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;

import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import com.ethlo.http.processors.auth.RealmUser;

public class WebExchangeDataProvider
{
    private final DataBufferRepository dataBufferRepository;
    private final PredicateConfig predicateConfig;
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
    private RealmUser user;
    private Throwable exception;

    public WebExchangeDataProvider(DataBufferRepository dataBufferRepository, final PredicateConfig predicateConfig)
    {
        this.dataBufferRepository = Objects.requireNonNull(dataBufferRepository);
        this.predicateConfig = predicateConfig;
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

    public Optional<RawProvider> getRawRequest()
    {
        return dataBufferRepository.get(ServerDirection.REQUEST, requestId);
    }

    public Optional<RawProvider> getRawResponse()
    {
        return dataBufferRepository.get(ServerDirection.RESPONSE, requestId);
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
        final Map<String, Object> params = new TreeMap<>();
        params.put("route_id", route.getId());
        params.put("route_uri", route.getUri());
        params.put("realm_claim", getUser().map(RealmUser::realm).orElse(null));
        params.put("user_claim", getUser().map(RealmUser::username).orElse(null));

        params.put("host", getRequestHeaders().getFirst(HttpHeaders.HOST));
        params.put("user_agent", getRequestHeaders().getFirst(HttpHeaders.USER_AGENT));

        params.put("request_content_type", Optional.ofNullable(getRequestHeaders().getContentType()).map(MediaType::toString).orElse(null));
        params.put("response_content_type", Optional.ofNullable(getResponseHeaders().getContentType()).map(MediaType::toString).orElse(null));

        params.put("timestamp", getTimestamp());
        params.put("gateway_request_id", getRequestId());
        params.put("method", getMethod().name());
        params.put("path", getPath().value());
        params.put("duration", getDuration().toMillis());
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

    public WebExchangeDataProvider user(RealmUser user)
    {
        this.user = user;
        return this;
    }

    public Optional<RealmUser> getUser()
    {
        return Optional.ofNullable(user);
    }

    public Optional<PredicateConfig> getPredicateConfig()
    {
        return Optional.ofNullable(predicateConfig);
    }

    public WebExchangeDataProvider exception(Throwable exc)
    {
        this.exception = exc;
        return this;
    }

    public Optional<Throwable> getException()
    {
        return Optional.ofNullable(exception);
    }
}
