package com.ethlo.http.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.netty.ContextUtil;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import jakarta.annotation.Nonnull;
import rawhttp.core.RawHttpHeaders;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CircuitBreakerHandler implements HandlerFunction<ServerResponse>
{
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerHandler.class);
    private final DataBufferRepository dataBufferRepository;

    public CircuitBreakerHandler(final DataBufferRepository dataBufferRepository)
    {
        this.dataBufferRepository = dataBufferRepository;
    }

    private static ByteBuffer extractHeaders(ServerHttpRequest request)
    {
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilder();
        request.getHeaders().forEach((name, values) -> values.forEach(value -> builder.with(name, value)));
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            final RawHttpHeaders headers = builder.build();
            headers.writeTo(baos);
            return ByteBuffer.wrap(baos.toByteArray());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public @Nonnull Mono<ServerResponse> handle(@Nonnull ServerRequest serverRequest)
    {
        final Optional<Exception> exc = serverRequest.attribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR).map(Exception.class::cast);
        exc.ifPresent(e -> logger.warn("An error occurred when routing request {} upstream: {}", serverRequest.exchange().getRequest().getId(), e.getMessage()));

        final Optional<PredicateConfig> config = ContextUtil.getLoggingConfig(serverRequest);
        logger.debug("Reading logging config: {}", config.orElse(null));
        return config
                .map(p ->
                {
                    if (p.request().mustBuffer())
                    {
                        return saveIncomingRequest(serverRequest)
                                .then(buildResponse(exc.orElse(null)));
                    }
                    return buildResponse(exc.orElse(null));
                })
                .orElse(buildResponse(exc.orElse(null)));
    }

    private Mono<ServerResponse> buildResponse(final Exception e)
    {
        return ServerResponse
                .status(504)
                .build();
    }

    private Mono<ServerResponse> saveIncomingRequest(ServerRequest serverRequest)
    {
        final ServerHttpRequest request = serverRequest.exchange().getRequest();
        final String requestId = request.getId();
        final HttpMethod method = request.getMethod();

        final ByteBuffer fakeRequestLine = ByteBuffer.wrap((method.name() + " / HTTP/1.1\r\n").getBytes(StandardCharsets.UTF_8));
        dataBufferRepository.write(ServerDirection.REQUEST, requestId, fakeRequestLine);
        dataBufferRepository.write(ServerDirection.REQUEST, requestId, extractHeaders(request));

        return serverRequest.exchange().getRequest().getBody()
                .publishOn(Schedulers.boundedElastic())
                .flatMapSequential(dataBuffer -> saveDataChunk(requestId, dataBuffer))
                .then(Mono.empty());
    }

    private Mono<Long> saveDataChunk(String requestId, DataBuffer dataBuffer)
    {
        try (final DataBuffer.ByteBufferIterator iter = dataBuffer.readableByteBuffers();)
        {
            long written = 0;
            while (iter.hasNext())
            {
                final ByteBuffer byteBuffer = iter.next();
                written += dataBufferRepository.write(ServerDirection.REQUEST, requestId, byteBuffer).join();
            }
            return Mono.just(written);
        } finally
        {
            DataBufferUtils.release(dataBuffer);
        }
    }
}
