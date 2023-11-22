package com.ethlo.http.netty;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.processors.LogPreProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

public class TagRequestIdGlobalFilter implements GlobalFilter, Ordered
{
    public static final String REQUEST_ID_ATTRIBUTE_NAME = "gateway-request-id";
    public static final String LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME = "log_capture_config";

    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdGlobalFilter.class);

    private final HttpLogger httpLogger;
    private final DataBufferRepository dataBufferRepository;
    private final LogPreProcessor logPreProcessor;
    private final AsyncPredicate<ServerWebExchange> predicate;

    public TagRequestIdGlobalFilter(final HttpLogger httpLogger, final DataBufferRepository dataBufferRepository, final LogPreProcessor logPreProcessor, AsyncPredicate<ServerWebExchange> predicate)
    {
        this.httpLogger = httpLogger;
        this.dataBufferRepository = dataBufferRepository;
        this.logPreProcessor = logPreProcessor;
        this.predicate = predicate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        return Mono.from(Flux.from(predicate.apply(exchange)).map(isMatch ->
        {
            if (isMatch)
            {
                final long started = System.nanoTime();
                final String requestId = exchange.getRequest().getId();
                logger.debug("Tagging request: {}", requestId);

                return chain.filter(exchange)
                        .contextWrite(ctx -> ctx.put(REQUEST_ID_ATTRIBUTE_NAME, requestId))
                        .publishOn(Schedulers.boundedElastic())
                        .doFinally(st ->
                        {
                            if (st.equals(SignalType.ON_COMPLETE) || st.equals(SignalType.ON_ERROR) || st.equals(SignalType.CANCEL))
                            {
                                handleCompletedRequest(exchange, requestId, Duration.ofNanos(System.nanoTime() - started));
                            }
                            else
                            {
                                logger.warn("Unhandled signal type {} - {}", requestId, st);
                            }
                        });
            }
            return chain.filter(exchange);
        })).then();
    }

    private void handleCompletedRequest(ServerWebExchange exchange, String requestId, final Duration duration)
    {
        logger.debug("Completed request {} in {}", requestId, duration);
        dataBufferRepository.finished(requestId);

        final ServerHttpRequest req = exchange.getRequest();
        final ServerHttpResponse res = exchange.getResponse();
        org.springframework.cloud.gateway.route.Route route = exchange.getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

        final WebExchangeDataProvider data = new WebExchangeDataProvider(dataBufferRepository)
                .route(route)
                .requestId(req.getId())
                .method(req.getMethod())
                .path(req.getPath())
                .uri(req.getURI())
                .statusCode(res.getStatusCode())
                .requestHeaders(req.getHeaders())
                .responseHeaders(res.getHeaders())
                .timestamp(OffsetDateTime.now())
                .duration(duration)
                .remoteAddress(req.getRemoteAddress());

        final WebExchangeDataProvider processed = logPreProcessor.process(data);

        httpLogger.accessLog(processed);

        // NOT in finally, as we do not want to delete data if it has not been properly processed
        dataBufferRepository.cleanup(requestId);
    }

    @Override
    public int getOrder()
    {
        return Integer.MIN_VALUE;
    }
}