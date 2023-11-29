package com.ethlo.http.netty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.processors.LogPreProcessor;
import jakarta.annotation.Nonnull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

public class TagRequestIdGlobalFilter implements WebFilter, Ordered
{
    public static final String REQUEST_ID_ATTRIBUTE_NAME = "gateway-request-id";
    public static final String LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME = "log_capture_config";

    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdGlobalFilter.class);

    private final HttpLogger httpLogger;
    private final DataBufferRepository dataBufferRepository;
    private final LogPreProcessor logPreProcessor;
    private final List<PredicateConfig> predicateConfigs;

    public TagRequestIdGlobalFilter(final HttpLogger httpLogger, final DataBufferRepository dataBufferRepository, final LogPreProcessor logPreProcessor, List<PredicateConfig> predicateConfigs)
    {
        this.httpLogger = httpLogger;
        this.dataBufferRepository = dataBufferRepository;
        this.logPreProcessor = logPreProcessor;
        this.predicateConfigs = predicateConfigs;
    }

    @Override
    public @Nonnull Mono<Void> filter(@Nonnull ServerWebExchange exchange, WebFilterChain chain)
    {
        return Flux.fromIterable(predicateConfigs)
                .filterWhen(c -> (Publisher<Boolean>) c.predicate().apply(exchange))
                .next()
                .flatMap(c -> prepareForLoggingIfApplicable(exchange, chain, c))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> prepareForLoggingIfApplicable(ServerWebExchange exchange, WebFilterChain chain, PredicateConfig predicateConfig)
    {
        final long started = System.nanoTime();
        final String requestId = exchange.getRequest().getId();
        exchange.getAttributes().put(TagRequestIdGlobalFilter.REQUEST_ID_ATTRIBUTE_NAME, requestId);

        if (exchange.getAttribute(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME) == null)
        {
            exchange.getAttributes().put(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME, predicateConfig);
            return chain.filter(exchange)
                    .contextWrite(ctx ->
                    {
                        logger.debug("Tagging request {}: {}", requestId, predicateConfig);
                        ctx.put(REQUEST_ID_ATTRIBUTE_NAME, requestId);
                        ctx.put(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME, predicateConfig);
                        return ctx;
                    })
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
        else
        {
            return chain.filter(exchange);
        }
    }

    private void handleCompletedRequest(ServerWebExchange exchange, String requestId, final Duration duration)
    {
        logger.debug("Completed request {} in {}", requestId, duration);
        dataBufferRepository.finished(requestId);

        final ServerHttpRequest req = exchange.getRequest();
        final ServerHttpResponse res = exchange.getResponse();
        final Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        final PredicateConfig predicateConfig = exchange.getAttribute(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME);

        final WebExchangeDataProvider data = new WebExchangeDataProvider(dataBufferRepository, predicateConfig)
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