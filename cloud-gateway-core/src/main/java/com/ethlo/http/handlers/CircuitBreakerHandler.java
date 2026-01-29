package com.ethlo.http.handlers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.netty.ContextUtil;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import jakarta.annotation.Nonnull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public class CircuitBreakerHandler implements HandlerFunction<ServerResponse>
{
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerHandler.class);
    private final DataBufferRepository dataBufferRepository;
    private final Scheduler ioScheduler;

    public CircuitBreakerHandler(final DataBufferRepository dataBufferRepository, final Scheduler ioScheduler)
    {
        this.dataBufferRepository = dataBufferRepository;
        this.ioScheduler = ioScheduler;
    }

    @Override
    public @Nonnull Mono<ServerResponse> handle(@Nonnull ServerRequest serverRequest)
    {
        final String requestId = serverRequest.exchange().getRequest().getId();
        final Optional<Exception> exc = serverRequest.attribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR).map(Exception.class::cast);

        exc.ifPresent(e -> logger.warn("Circuit breaker fallback for request {}: {}", requestId, e.getMessage()));

        final Optional<PredicateConfig> config = ContextUtil.getLoggingConfig(serverRequest);
        final Mono<ServerResponse> gatewayTimeoutResponse = ServerResponse.status(HttpStatus.GATEWAY_TIMEOUT).build();

        return config
                .filter(p -> p.request().mustBuffer())
                .map(p -> drainRequestBody(serverRequest).then(gatewayTimeoutResponse))
                .orElse(gatewayTimeoutResponse);
    }

    /**
     * Drains the request body into the repository so it can be logged,
     * even though there is no upstream to consume it.
     */
    private Mono<Void> drainRequestBody(ServerRequest serverRequest)
    {
        final String requestId = serverRequest.exchange().getRequest().getId();

        return serverRequest.bodyToFlux(DataBuffer.class)
                .doOnNext(db -> offloadWrite(db, requestId))
                .then();
    }

    private void offloadWrite(DataBuffer db, String requestId)
    {
        // Retain to protect from Netty recycling during async offload
        DataBufferUtils.retain(db);

        Mono.fromRunnable(() -> {
                    try (DataBuffer.ByteBufferIterator iterator = db.readableByteBuffers())
                    {
                        while (iterator.hasNext())
                        {
                            // Synchronous write to the body-only file
                            dataBufferRepository.writeSync(ServerDirection.REQUEST, requestId, iterator.next());
                        }
                    } finally
                    {
                        // Release back to Netty pool
                        DataBufferUtils.release(db);
                    }
                })
                .subscribeOn(ioScheduler)
                .subscribe();
    }
}