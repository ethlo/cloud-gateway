package com.ethlo.http.netty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.logger.LoggingFilterService;
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
    public static final String LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME = TagRequestIdGlobalFilter.class.getName() + ".captureConfig";
    private static final String EXCEPTION_ATTRIBUTE_NAME = TagRequestIdGlobalFilter.class.getName() + ".exception";
    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdGlobalFilter.class);

    private final LoggingFilterService loggingFilterService;
    private final SequentialDelegateLogger httpLogger;
    private final DataBufferRepository dataBufferRepository;
    private final LogPreProcessor logPreProcessor;
    private final List<PredicateConfig> predicateConfigs;
    private final Scheduler ioScheduler;

    public TagRequestIdGlobalFilter(final LoggingFilterService loggingFilterService, final SequentialDelegateLogger httpLogger, final DataBufferRepository dataBufferRepository, final LogPreProcessor logPreProcessor, List<PredicateConfig> predicateConfigs, Scheduler ioScheduler)
    {
        this.loggingFilterService = loggingFilterService;
        this.httpLogger = httpLogger;
        this.dataBufferRepository = dataBufferRepository;
        this.logPreProcessor = logPreProcessor;
        this.predicateConfigs = predicateConfigs;
        this.ioScheduler = ioScheduler;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull Mono<Void> filter(@Nonnull ServerWebExchange exchange, GatewayFilterChain chain)
    {
        final Flux<PredicateConfig> filteredConfigs = Flux.fromIterable(predicateConfigs)
                .filterWhen(c -> (Publisher<Boolean>) c.predicate().apply(exchange));

        return filteredConfigs
                .hasElements()  // Check if any predicate passed
                .flatMapMany(hasMatches -> {
                    if (hasMatches)
                    {
                        // If there's at least one match, run prepareForLoggingIfApplicable once
                        return filteredConfigs.take(1)
                                .flatMap(predicateConfig ->
                                        prepareForLoggingIfApplicable(exchange, chain, loggingFilterService.merge(predicateConfig)));
                    }
                    else
                    {
                        // If no matches, run chain.filter(exchange)
                        return chain.filter(exchange).flux();
                    }
                })
                .next();
    }

    private Mono<Void> prepareForLoggingIfApplicable(ServerWebExchange exchange, GatewayFilterChain chain, PredicateConfig predicateConfig)
    {
        final long started = System.nanoTime();
        final String requestId = exchange.getRequest().getId();
        logger.debug("Tagging request {}: {}", requestId, predicateConfig);
        exchange.getAttributes().put(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME, predicateConfig);
        return chain.filter(exchange)
                .doOnError(exc -> exchange.getAttributes().put(TagRequestIdGlobalFilter.EXCEPTION_ATTRIBUTE_NAME, exc))
                .doFinally(signalType ->
                {
                    final Throwable exception = Optional.ofNullable((Throwable) exchange.getAttribute(TagRequestIdGlobalFilter.EXCEPTION_ATTRIBUTE_NAME))
                            .or(() -> Optional.ofNullable(exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR)))
                            .orElse(null);

                    ioScheduler.schedule(() -> saveDataAndCleanupIfApplicable(exchange, predicateConfig, requestId, started, exception).join());
                });
    }

    private CompletableFuture<AccessLogResult> saveDataAndCleanupIfApplicable(ServerWebExchange exchange, PredicateConfig predicateConfig, String requestId, long started, final Throwable exc)
    {
        final CompletableFuture<AccessLogResult> result = saveDataUsingProviders(exchange, requestId, predicateConfig, Duration.ofNanos(System.nanoTime() - started), exc);
        return result.whenComplete((logResult, loggerException) ->
        {
            dataBufferRepository.close(requestId);

            boolean cleaned = false;
            if (loggerException != null)
            {
                logger.error("There was an error logging: {}", loggerException.getMessage(), loggerException);
            }
            else
            {
                if (logResult.isOk())
                {
                    dataBufferRepository.cleanup(requestId);
                    cleaned = true;
                }
            }

            if (!cleaned)
            {
                final Pair<String, String> filenames = dataBufferRepository.getBufferFileNames(requestId);
                logger.warn("There were problems storing data for request {}. The buffer files are left behind: {} {}. Details: {}", requestId, filenames.getFirst(), filenames.getSecond(), Optional.ofNullable(logResult).map(AccessLogResult::getProcessingErrors).orElse(List.of()));
            }
        });
    }

    private CompletableFuture<AccessLogResult> saveDataUsingProviders(ServerWebExchange exchange, String requestId, final PredicateConfig predicateConfig, final Duration duration, final Throwable exception)
    {
        final ServerHttpRequest req = exchange.getRequest();
        final ServerHttpResponse res = exchange.getResponse();
        final Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        final HttpStatusCode httpStatusCode = determineStatusCode(exception, exchange.getResponse().getStatusCode());

        logger.debug("Completed request {} in {}: {}", requestId, duration, predicateConfig);

        final WebExchangeDataProvider data = new WebExchangeDataProvider(dataBufferRepository, predicateConfig)
                .route(route)
                .requestId(req.getId())
                .method(req.getMethod())
                .path(req.getPath())
                .uri(req.getURI())
                .exception(exception)
                .statusCode(httpStatusCode)
                .requestHeaders(req.getHeaders())
                .responseHeaders(res.getHeaders())
                .timestamp(OffsetDateTime.now())
                .duration(duration)
                .remoteAddress(req.getRemoteAddress());

        final WebExchangeDataProvider processed = logPreProcessor.process(data);

        logger.debug("Logging HTTP data for request {}", requestId);
        return httpLogger.accessLog(processed);
    }

    private HttpStatusCode determineStatusCode(final Throwable exc, final HttpStatusCode responseStatusCode)
    {
        if (exc != null)
        {
            return Optional.of(exc)
                    .filter(e -> ResponseStatusException.class.isAssignableFrom(e.getClass()))
                    .map(ResponseStatusException.class::cast)
                    .map(ErrorResponseException::getStatusCode)
                    .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseStatusCode;
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}