package com.ethlo.http.model;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import com.ethlo.http.DataBufferRepository;
import com.ethlo.http.Route;
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
    private String path;
    private URI uri;
    private HttpStatusCode statusCode;
    private HttpHeaders requestHeaders;
    private HttpHeaders responseHeaders;
    private OffsetDateTime timestamp;
    private Duration duration;
    private InetSocketAddress remoteAddress;
    private RealmUser user;
    private Throwable exception;
    private Runnable cleanupTask;

    public WebExchangeDataProvider(DataBufferRepository dataBufferRepository, final PredicateConfig predicateConfig)
    {
        this.dataBufferRepository = Objects.requireNonNull(dataBufferRepository);
        this.predicateConfig = Objects.requireNonNull(predicateConfig);
    }

    public void cleanupTask(Runnable cleanupTask)
    {
        this.cleanupTask = cleanupTask;
    }

    public void cleanup()
    {
        if (cleanupTask != null)
        {
            cleanupTask.run();
        }
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

    public WebExchangeDataProvider path(String path)
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

    public Optional<BodyProvider> getRequestBody()
    {
        return dataBufferRepository.get(ServerDirection.REQUEST, requestId, requestHeaders.getFirst(HttpHeaders.CONTENT_ENCODING));
    }

    public Optional<BodyProvider> getResponseBody()
    {
        return dataBufferRepository.get(ServerDirection.RESPONSE, requestId, responseHeaders.getFirst(HttpHeaders.CONTENT_ENCODING));
    }

    public String getRequestId()
    {
        return requestId;
    }

    public HttpMethod getMethod()
    {
        return method;
    }

    public String getPath()
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
        params.put("route_id", route.id());
        params.put("route_uri", route.uri().toString());
        params.put("realm_claim", getUser().map(RealmUser::realm).orElse(null));
        params.put("user_claim", getUser().map(RealmUser::username).orElse(null));

        params.put("host", getRequestHeaders().getFirst(HttpHeaders.HOST));
        params.put("user_agent", getRequestHeaders().getFirst(HttpHeaders.USER_AGENT));

        params.put("request_content_type", Optional.ofNullable(getRequestHeaders().getContentType()).map(MediaType::toString).orElse(null));
        params.put("response_content_type", Optional.ofNullable(getResponseHeaders().getContentType()).map(MediaType::toString).orElse(null));

        params.put("timestamp", getTimestamp());
        params.put("gateway_request_id", getRequestId());
        params.put("method", getMethod().name());
        params.put("path", getPath());
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

    public PredicateConfig getPredicateConfig()
    {
        return predicateConfig;
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
