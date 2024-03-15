package com.ethlo.http.netty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.data.util.Pair;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.logger.delegate.SequentialDelegateLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.processors.LogPreProcessor;
import jakarta.annotation.Nonnull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public class TagRequestIdGlobalFilter implements GlobalFilter, Ordered
{
    public static final String LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME = "log_capture_config";

    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdGlobalFilter.class);

    private final SequentialDelegateLogger httpLogger;
    private final DataBufferRepository dataBufferRepository;
    private final LogPreProcessor logPreProcessor;
    private final List<PredicateConfig> predicateConfigs;
    private final Scheduler ioScheduler;

    public TagRequestIdGlobalFilter(final SequentialDelegateLogger httpLogger, final DataBufferRepository dataBufferRepository, final LogPreProcessor logPreProcessor, List<PredicateConfig> predicateConfigs, Scheduler ioScheduler)
    {
        this.httpLogger = httpLogger;
        this.dataBufferRepository = dataBufferRepository;
        this.logPreProcessor = logPreProcessor;
        this.predicateConfigs = predicateConfigs;
        this.ioScheduler = ioScheduler;
    }

    @Override
    public @Nonnull Mono<Void> filter(@Nonnull ServerWebExchange exchange, GatewayFilterChain chain)
    {
        return Flux.fromIterable(predicateConfigs)
                .filterWhen(c -> (Publisher<Boolean>) c.predicate().apply(exchange))
                .flatMap(c -> prepareForLoggingIfApplicable(exchange, chain, c))
                .switchIfEmpty(chain.filter(exchange))
                .next();
    }

    private Mono<Void> prepareForLoggingIfApplicable(ServerWebExchange exchange, GatewayFilterChain chain, PredicateConfig predicateConfig)
    {
        final long started = System.nanoTime();
        final String requestId = exchange.getRequest().getId();
        logger.debug("Tagging request {}: {}", requestId, predicateConfig);
        exchange.getAttributes().put(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME, predicateConfig);
        return chain.filter(exchange)
                .doFinally(signalType ->
                        ioScheduler.schedule(() ->
                                saveDataAndCleanupIfApplicable(exchange, predicateConfig, requestId, started)));
    }

    private void saveDataAndCleanupIfApplicable(ServerWebExchange exchange, PredicateConfig predicateConfig, String requestId, long started)
    {
        final CompletableFuture<AccessLogResult> result = saveDataUsingProviders(exchange, requestId, predicateConfig, Duration.ofNanos(System.nanoTime() - started));
        result.whenComplete((logResult, exc) ->
        {
            dataBufferRepository.close(requestId);
            if (logResult.isOk())
            {
                dataBufferRepository.cleanup(requestId);
            }
            else
            {
                final Pair<String, String> filenames = dataBufferRepository.getBufferFileNames(requestId);
                logger.warn("There were problems storing data for request {}. The buffer files are left behind: {} {}. Details: {}", requestId, filenames.getFirst(), filenames.getSecond(), logResult.getProcessingErrors());
            }
        });
    }

    private CompletableFuture<AccessLogResult> saveDataUsingProviders(ServerWebExchange exchange, String requestId, final PredicateConfig predicateConfig, final Duration duration)
    {
        final ServerHttpRequest req = exchange.getRequest();
        final ServerHttpResponse res = exchange.getResponse();
        final Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

        logger.debug("Completed request {} in {}: {}", requestId, duration, predicateConfig);

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

        logger.debug("Logging HTTP data for request {}", requestId);
        return httpLogger.accessLog(processed);
    }

    @Override
    public int getOrder()
    {
        return Integer.MIN_VALUE;
    }
}