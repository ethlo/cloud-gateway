package com.ethlo.http.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.match.LogOptions;
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
    private static final String PROCESSED_KEY = CircuitBreakerHandler.class + ".PROCESSED";
    private final DataBufferRepository dataBufferRepository;

    public CircuitBreakerHandler(final DataBufferRepository dataBufferRepository)
    {
        this.dataBufferRepository = dataBufferRepository;
    }

    @Override
    public @Nonnull Mono<ServerResponse> handle(@Nonnull ServerRequest serverRequest)
    {
        final Optional<Exception> exc = serverRequest.attribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR).map(Exception.class::cast);
        exc.ifPresent(e -> logger.info("An error occurred when routing upstream: {}", e.toString()));

        final Optional<PredicateConfig> config = ContextUtil.getLoggingConfig(serverRequest);
        logger.debug("Reading logging config: {}", config.orElse(null));
        return config
                .filter(predicateConfig -> predicateConfig.request().body() == LogOptions.BodyProcessing.STORE)
                .map(p -> saveIncomingRequest(serverRequest))
                .orElse(ServerResponse.status(504)
                        .build());
    }

    private Mono<ServerResponse> saveIncomingRequest(ServerRequest serverRequest)
    {
        final ServerHttpRequest request = serverRequest.exchange().getRequest();
        final String requestId = request.getId();
        final HttpMethod method = request.getMethod();

        final byte[] fakeRequestLine = (method.name() + " / HTTP/1.1\r\n").getBytes(StandardCharsets.UTF_8);
        dataBufferRepository.write(ServerDirection.REQUEST, requestId, fakeRequestLine);
        dataBufferRepository.write(ServerDirection.REQUEST, requestId, extractHeaders(request));

        return serverRequest.exchange().getRequest().getBody()
                .flatMapSequential(dataBuffer -> saveDataChunk(requestId, dataBuffer)
                        .publishOn(Schedulers.boundedElastic()))
                .then(ServerResponse.status(504).build());
    }

    private Mono<Integer> saveDataChunk(String requestId, DataBuffer dataBuffer)
    {
        try (final InputStream in = dataBuffer.asInputStream())
        {
            final int copied = StreamUtils.copy(in, dataBufferRepository.getOutputStream(ServerDirection.REQUEST, requestId));
            dataBufferRepository.appendSizeAvailable(ServerDirection.REQUEST, requestId, copied);
            DataBufferUtils.release(dataBuffer);
            return Mono.just(copied);
        }
        catch (IOException exc)
        {
            return Mono.error(exc);
        }
    }

    private static byte[] extractHeaders(ServerHttpRequest request)
    {
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilder();
        request.getHeaders().forEach((name, values) -> values.forEach(value -> builder.with(name, value)));
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            final RawHttpHeaders headers = builder.build();
            headers.writeTo(baos);
            return baos.toByteArray();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
