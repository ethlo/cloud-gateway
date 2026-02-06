package com.ethlo.http.model;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(WebExchangeDataProvider.class);
    private final DataBufferRepository dataBufferRepository;
    private final PredicateConfig predicateConfig;
    private String requestId;
    private Route route;
    private HttpMethod method;
    private String path;
    private URI uri;
    private HttpStatusCode statusCode;
    private String protocol;
    private HeaderProvider requestHeaders;
    private HeaderProvider responseHeaders;
    private OffsetDateTime timestamp;
    private Duration duration;
    private InetSocketAddress remoteAddress;
    private RealmUser user;
    private Throwable exception;
    private Runnable cleanupTask;
    private Map<String, Object> metamap;

    public WebExchangeDataProvider(DataBufferRepository dataBufferRepository, final PredicateConfig predicateConfig)
    {
        this.dataBufferRepository = Objects.requireNonNull(dataBufferRepository);
        this.predicateConfig = Objects.requireNonNull(predicateConfig);
    }

    public WebExchangeDataProvider cleanupTask(Runnable cleanupTask)
    {
        this.cleanupTask = cleanupTask;
        return this;
    }

    public void cleanup()
    {
        if (cleanupTask != null)
        {
            cleanupTask.run();
        }
        else
        {
            logger.warn("No cleanup task for request {}", requestId);
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
        return dataBufferRepository.getBody(ServerDirection.REQUEST, requestId);
    }

    public Optional<BodyProvider> getResponseBody()
    {
        return dataBufferRepository.getBody(ServerDirection.RESPONSE, requestId);
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
        if (requestHeaders == null)
        {
            requestHeaders = new HeaderProvider(dataBufferRepository, requestId, ServerDirection.REQUEST);
        }
        return requestHeaders.getHeaders();
    }

    public HttpHeaders getResponseHeaders()
    {
        if (responseHeaders == null)
        {
            responseHeaders = new HeaderProvider(dataBufferRepository, requestId, ServerDirection.RESPONSE);
        }
        return responseHeaders.getHeaders();
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
        if (metamap == null)
        {
            metamap = new TreeMap<>();
            metamap.put("route_id", route.id());
            metamap.put("route_uri", route.uri().toString());
            metamap.put("realm_claim", getUser().map(RealmUser::realm).orElse(null));
            metamap.put("user_claim", getUser().map(RealmUser::username).orElse(null));

            metamap.put("host", getRequestHeaders().getFirst(HttpHeaders.HOST));
            metamap.put("user_agent", getRequestHeaders().getFirst(HttpHeaders.USER_AGENT));

            metamap.put("request_content_type", Optional.ofNullable(getRequestHeaders().getContentType()).map(MediaType::toString).orElse(null));
            metamap.put("response_content_type", Optional.ofNullable(getResponseHeaders().getContentType()).map(MediaType::toString).orElse(null));

            metamap.put("timestamp", getTimestamp());
            metamap.put("gateway_request_id", getRequestId());
            metamap.put("method", getMethod().name());
            metamap.put("path", getPath());
            metamap.put("duration", getDuration().toMillis());
            metamap.put("status", getStatusCode().value());
            metamap.put("is_error", getStatusCode().isError());
            metamap.put("request_headers", getRequestHeaders());
            metamap.put("response_headers", getResponseHeaders());
            metamap = Collections.unmodifiableMap(metamap);
        }
        return metamap;
    }

    public Route getRoute()
    {
        return route;
    }

    public WebExchangeDataProvider protocol(final String protocol)
    {
        this.protocol = protocol;
        return this;
    }

    public String getProtocol()
    {
        return protocol;
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
