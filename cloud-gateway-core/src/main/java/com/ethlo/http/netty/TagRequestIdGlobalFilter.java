package com.ethlo.http.netty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.NonNull;
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

    public TagRequestIdGlobalFilter(LoggingFilterService loggingFilterService, SequentialDelegateLogger httpLogger,
                                    DataBufferRepository repository, LogPreProcessor logPreProcessor,
                                    List<PredicateConfig> predicateConfigs, Scheduler ioScheduler)
    {
        this.loggingFilterService = loggingFilterService;
        this.httpLogger = httpLogger;
        this.repository = repository;
        this.logPreProcessor = logPreProcessor;
        this.predicateConfigs = predicateConfigs;
        this.ioScheduler = ioScheduler;
    }

    @Override
    public @Nonnull Mono<@NonNull Void> filter(@Nonnull ServerWebExchange exchange, @org.jetbrains.annotations.NotNull GatewayFilterChain chain)
    {
        final ServerHttpRequest requestWithId = new RequestIdWrapper(exchange.getRequest(), RequestIdWrapper.generateId());
        final ServerWebExchange exchangeWithId = exchange.mutate().request(requestWithId).build();

        //noinspection unchecked
        return Flux.fromIterable(predicateConfigs)
                .filterWhen(c -> Mono.from((Publisher<Boolean>) c.predicate().apply(exchangeWithId)))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(configOpt -> configOpt
                        .map(config -> prepareLogging(exchangeWithId, chain, loggingFilterService.merge(config)))
                        .orElseGet(() -> chain.filter(exchangeWithId))
                );
    }

    private Mono<@NonNull Void> prepareLogging(ServerWebExchange exchange, GatewayFilterChain chain, PredicateConfig config)
    {
        final long start = System.nanoTime();
        final String id = exchange.getRequest().getId();

        final ServerWebExchange decorated = exchange.mutate()
                .request(new RequestCapture(exchange.getRequest(), id))
                .response(new ResponseCapture(exchange.getResponse(), id))
                .build();

        return chain.filter(decorated)
                .doOnError(e -> decorated.getAttributes().put(EXCEPTION_ATTRIBUTE_NAME, e))
                .doFinally(sig -> {
                    // We fire the logging chain here
                    Throwable exc = (Throwable) decorated.getAttribute(EXCEPTION_ATTRIBUTE_NAME);
                    saveLog(decorated, config, id, start, exc)
                            .subscribeOn(ioScheduler) // Offload the final aggregation and cleanup
                            .subscribe();
                });
    }

    private Mono<@NonNull Void> saveLog(ServerWebExchange exchange, PredicateConfig config, String id, long start, Throwable exc)
    {
        final Duration duration = Duration.ofNanos(System.nanoTime() - start);
        final WebExchangeDataProvider provider = createProvider(exchange, id, config, duration, exc);

        return httpLogger.accessLog(logPreProcessor.process(provider))
                .flatMap(res -> {
                    repository.close(id);
                    if (res.isOk())
                    {
                        repository.cleanup(id);
                    }
                    else
                    {
                        warnLeftoverFiles(id, res);
                    }
                    return Mono.empty();
                })
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

    private String getProtocol(ServerWebExchange exchange)
    {
        return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto"))
                .map(p -> p.toUpperCase() + "/1.1").orElse("HTTP/1.1");
    }

    private void offload(DataBuffer db, ServerDirection dir, String id)
    {
        DataBufferUtils.retain(db);
        Mono.fromRunnable(() ->
        {
            try (DataBuffer.ByteBufferIterator it = db.readableByteBuffers())
            {
                while (it.hasNext()) repository.writeSync(dir, id, it.next());
            } finally
            {
                DataBufferUtils.release(db);
            }
        }).subscribeOn(ioScheduler).subscribe();
    }

    private HttpStatusCode determineStatusCode(Throwable exc, HttpStatusCode res)
    {
        if (exc instanceof ResponseStatusException r) return r.getStatusCode();
        return exc != null ? HttpStatus.INTERNAL_SERVER_ERROR : res;
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private class RequestCapture extends ServerHttpRequestDecorator
    {
        private final String requestId;
        private final AtomicBoolean head = new AtomicBoolean();

        RequestCapture(ServerHttpRequest serverHttpRequest, String requestId)
        {
            super(serverHttpRequest);
            this.requestId = requestId;
        }

        @Override
        public @Nonnull Flux<@NonNull DataBuffer> getBody()
        {
            return super.getBody().doOnNext(db -> offload(db, ServerDirection.REQUEST, requestId));
        }
    }

    private class ResponseCapture extends ServerHttpResponseDecorator
    {
        private final String requestId;
        private final AtomicBoolean head = new AtomicBoolean();

        ResponseCapture(ServerHttpResponse serverHttpResponse, String requestId)
        {
            super(serverHttpResponse);
            this.requestId = requestId;
        }

        @Override
        public @Nonnull Mono<@NonNull Void> writeWith(@Nonnull Publisher<? extends DataBuffer> body)
        {
            return super.writeWith(Flux.from(body).doOnNext(db -> offload(db, ServerDirection.RESPONSE, requestId)));
        }
    }
}