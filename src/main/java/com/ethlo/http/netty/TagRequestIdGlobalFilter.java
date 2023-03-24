package com.ethlo.http.netty;

import static com.ethlo.http.util.HttpMessageUtil.findBodyPositionInStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.match.RequestMatchingConfiguration;
import com.ethlo.http.match.RequestPattern;
import com.ethlo.http.processors.HeaderFilterConfiguration;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

@Component
public class TagRequestIdGlobalFilter implements GlobalFilter, Ordered
{
    public static final String REQUEST_ID_ATTRIBUTE_NAME = "gateway-request-id";
    public static final String LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME = "log_capture_config";

    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdGlobalFilter.class);

    private final HttpLogger httpLogger;
    private final DataBufferRepository dataBufferRepository;
    private final RequestMatchingConfiguration requestMatchingConfiguration;
    private final HeaderFilterConfiguration headerFilterConfiguration;

    public TagRequestIdGlobalFilter(final HttpLogger httpLogger, final DataBufferRepository dataBufferRepository, final RequestMatchingConfiguration requestMatchingConfiguration, final HeaderFilterConfiguration headerFilterConfiguration)
    {
        this.httpLogger = httpLogger;
        this.dataBufferRepository = dataBufferRepository;
        this.requestMatchingConfiguration = requestMatchingConfiguration;
        this.headerFilterConfiguration = headerFilterConfiguration;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        final Optional<RequestPattern> match = requestMatchingConfiguration.matches(exchange.getRequest());
        if (match.isPresent())
        {
            final long started = System.nanoTime();
            final String requestId = exchange.getRequest().getId();
            logger.debug("Tagging request: {}", requestId);

            return chain.filter(exchange)
                    .contextWrite(ctx ->
                            ctx.put(REQUEST_ID_ATTRIBUTE_NAME, requestId)
                                    .put(LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME, match.get()))
                    .publishOn(Schedulers.boundedElastic())
                    .doFinally(st ->
                    {
                        if (st.equals(SignalType.ON_COMPLETE) || st.equals(SignalType.ON_ERROR))
                        {
                            handleCompletedRequest(exchange, requestId, Duration.ofNanos(System.nanoTime() - started));
                        }
                        else
                        {
                            logger.warn("Signal type {} - {}", requestId, st);
                        }
                    });
        }
        return chain.filter(exchange);
    }

    private void handleCompletedRequest(ServerWebExchange exchange, String requestId, final Duration duration)
    {
        logger.debug("Completed request {} in {}", requestId, duration);
        dataBufferRepository.finished(requestId);

        try (final BufferedInputStream requestData = dataBufferRepository.get(DataBufferRepository.Operation.REQUEST, requestId);
             final BufferedInputStream responseData = dataBufferRepository.get(DataBufferRepository.Operation.RESPONSE, requestId))
        {
            if (requestData != null)
            {
                findBodyPositionInStream(requestData);
            }

            if (responseData != null)
            {
                findBodyPositionInStream(responseData);
            }

            httpLogger.accessLog(createAccessLogEntryData(exchange, duration), requestData, responseData);

            // NOT in finally, as we do not want to delete data if it has not been properly processed
            dataBufferRepository.cleanup(requestId);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> createAccessLogEntryData(ServerWebExchange exchange, final Duration duration)
    {
        final Map<String, Object> data = new HashMap<>();
        final ServerHttpRequest req = exchange.getRequest();
        final ServerHttpResponse res = exchange.getResponse();
        data.put("gateway_request_id", req.getId());
        data.put("method", req.getMethod().name());
        data.put("path", req.getPath().value());
        data.put("status", res.getStatusCode().value());
        data.put("request_headers", headerFilterConfiguration.getRequest().filter(req.getHeaders()));
        data.put("response_headers", headerFilterConfiguration.getResponse().filter(res.getHeaders()));
        data.put("timestamp", OffsetDateTime.now());
        data.put("request_size", req.getHeaders().getContentLength());
        data.put("response_size", res.getHeaders().getContentLength());
        data.put("response_time", duration.toMillis());
        data.put("remote_address", Optional.ofNullable(req.getRemoteAddress()).map(InetSocketAddress::getHostString).orElse(null));
        return data;
    }

    @Override
    public int getOrder()
    {
        return Integer.MIN_VALUE;
    }
}