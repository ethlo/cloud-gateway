package com.ethlo.http.netty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
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
    private final DataBufferRepository repository;
    private final LogPreProcessor logPreProcessor;
    private final List<PredicateConfig> predicateConfigs;
    private final Scheduler ioScheduler;
    private final boolean autoCleanup;

    public TagRequestIdGlobalFilter(LoggingFilterService loggingFilterService, SequentialDelegateLogger httpLogger,
                                    DataBufferRepository repository, LogPreProcessor logPreProcessor,
                                    List<PredicateConfig> predicateConfigs, Scheduler ioScheduler,
                                    final boolean autoCleanup)
    {
        this.loggingFilterService = loggingFilterService;
        this.httpLogger = httpLogger;
        this.repository = repository;
        this.logPreProcessor = logPreProcessor;
        this.predicateConfigs = predicateConfigs;
        this.ioScheduler = ioScheduler;
        this.autoCleanup = autoCleanup;
    }

    @Override
    public @Nonnull Mono<@NonNull Void> filter(@Nonnull ServerWebExchange exchange, @org.jetbrains.annotations.NotNull GatewayFilterChain chain)
    {
        final long started = System.nanoTime();

        final String requestId = RequestIdWrapper.generateId();
        final ServerHttpRequest requestWithId = new RequestIdWrapper(exchange.getRequest(), requestId);
        final ServerWebExchange exchangeWithId = exchange.mutate().request(requestWithId).build();

        //noinspection unchecked
        return Flux.fromIterable(predicateConfigs)
                .filterWhen(c -> Mono.from((Publisher<Boolean>) c.predicate().apply(exchangeWithId)))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(configOpt -> configOpt
                        .map(config -> prepareLogging(started, exchangeWithId, chain, loggingFilterService.merge(config)))
                        .orElseGet(() -> wrapChainWithStatusMapping(exchangeWithId, chain)) // Handle status even if not logging
                );
    }

    private Mono<@NonNull Void> prepareLogging(final long started, ServerWebExchange exchange, GatewayFilterChain chain, PredicateConfig config)
    {
        final String id = exchange.getRequest().getId();

        final ServerWebExchange decorated = exchange.mutate()
                .request(new RequestCapture(exchange.getRequest(), id))
                .response(new ResponseCapture(exchange.getResponse(), id))
                .build();

        return wrapChainWithStatusMapping(decorated, chain)
                .doOnError(e -> decorated.getAttributes().put(EXCEPTION_ATTRIBUTE_NAME, e))
                // Use flatMap (via onErrorResume/then) to ensure the logging completes
                // BEFORE the exchange signal is finalized
                .onErrorResume(e -> saveLog(decorated, config, id, started, e).then(Mono.error(e)))
                .then(Mono.defer(() -> {
                    final Throwable exc = decorated.getAttribute(EXCEPTION_ATTRIBUTE_NAME);
                    return saveLog(decorated, config, id, started, exc);
                }));
    }

    private Mono<@NonNull Void> wrapChainWithStatusMapping(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        return chain.filter(exchange)
                .onErrorResume(ex ->
                {
                    if (isReset(ex))
                    {
                        exchange.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream Reset", ex));
                    }
                    return Mono.error(ex);
                });
    }

    private boolean isReset(Throwable ex)
    {
        return ex instanceof io.netty.channel.unix.Errors.NativeIoException ||
                (ex != null && ex.getMessage() != null && ex.getMessage().contains("Connection reset"));
    }

    private Mono<@NonNull Void> saveLog(ServerWebExchange exchange, PredicateConfig config, String id, long start, Throwable exc)
    {
        final Duration duration = Duration.ofNanos(System.nanoTime() - start);

        repository.markComplete(ServerDirection.REQUEST, id);
        repository.markComplete(ServerDirection.RESPONSE, id);

        final WebExchangeDataProvider provider = createProvider(exchange, id, config, duration, exc);

        final Runnable cleanupTask = () ->
        {
            repository.close(id);
            repository.cleanup(id);
        };

        provider.cleanupTask(cleanupTask);

        return httpLogger.accessLog(logPreProcessor.process(provider))
                .flatMap(res -> {
                    // Perform cleanup ONLY after accessLog is done
                    repository.close(id);
                    if (res.isOk())
                    {
                        if (autoCleanup)
                        {
                            cleanupTask.run();
                        }
                    }
                    else
                    {
                        warnLeftoverFiles(id, res);
                    }
                    return Mono.empty();
                })
                // Explicitly ensure this whole sequence runs on the IO thread
                .subscribeOn(ioScheduler)
                .then();
    }

    private void warnLeftoverFiles(String requestId, AccessLogResult logResult)
    {
        final Pair<@NonNull String, @NonNull String> filenames = repository.getBufferFileNames(requestId);
        logger.warn("Problems storing data for request {}. Files left: {} {}. Errors: {}",
                requestId, filenames.getFirst(), filenames.getSecond(), logResult.getProcessingErrors()
        );
    }

    private WebExchangeDataProvider createProvider(ServerWebExchange exchange, String id, PredicateConfig config, Duration d, Throwable e)
    {
        return new WebExchangeDataProvider(repository, config)
                .route(exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR))
                .requestId(id).method(exchange.getRequest().getMethod()).path(exchange.getRequest().getPath())
                .uri(exchange.getRequest().getURI()).exception(e).statusCode(determineStatusCode(e, exchange.getResponse().getStatusCode()))
                .requestHeaders(exchange.getRequest().getHeaders()).responseHeaders(exchange.getResponse().getHeaders())
                .timestamp(OffsetDateTime.now()).duration(d).remoteAddress(exchange.getRequest().getRemoteAddress());
    }

    private HttpStatusCode determineStatusCode(Throwable e, @Nullable HttpStatusCode statusCode)
    {
        if (isReset(e))
        {
            return HttpStatus.BAD_GATEWAY;
        }
        return statusCode;
    }

    private Mono<@NonNull Void> offload(DataBuffer db, ServerDirection dir, String id)
    {
        DataBufferUtils.retain(db); // Hold the memory
        return Mono.fromRunnable(() ->
                {
                    try (DataBuffer.ByteBufferIterator it = db.readableByteBuffers())
                    {
                        while (it.hasNext())
                        {
                            repository.writeSync(dir, id, it.next());
                        }
                    } finally
                    {
                        DataBufferUtils.release(db); // Release ONLY after IO is done
                    }
                }).subscribeOn(ioScheduler)
                .then(); // Ensure this runs on the IO thread
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private class RequestCapture extends ServerHttpRequestDecorator
    {
        private final String requestId;

        RequestCapture(ServerHttpRequest serverHttpRequest, String requestId)
        {
            super(serverHttpRequest);
            this.requestId = requestId;
        }

        @NotNull
        @Override
        public Flux<@NonNull DataBuffer> getBody()
        {
            return super.getBody().concatMap(db ->
                    offload(db, ServerDirection.REQUEST, requestId)
                            .then(Mono.just(db)) // IMPORTANT: Don't release 'db' back to the stream until offload is done
            );
        }
    }

    private class ResponseCapture extends ServerHttpResponseDecorator
    {
        private final String requestId;

        ResponseCapture(ServerHttpResponse serverHttpResponse, String requestId)
        {
            super(serverHttpResponse);
            this.requestId = requestId;
        }

        @NotNull
        @Override
        public Mono<@NonNull Void> writeWith(@NotNull Publisher<? extends DataBuffer> body)
        {
            return super.writeWith(Flux.from(body).concatMap(db ->
                    offload(db, ServerDirection.RESPONSE, requestId)
                            .then(Mono.just(db))
            ));
        }
    }
}