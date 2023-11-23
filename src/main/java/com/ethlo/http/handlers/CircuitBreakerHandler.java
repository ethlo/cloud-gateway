package com.ethlo.http.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.ServerDirection;
import jakarta.annotation.Nonnull;
import rawhttp.core.RawHttpHeaders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CircuitBreakerHandler implements HandlerFunction<ServerResponse>
{
    private final DataBufferRepository dataBufferRepository;

    public CircuitBreakerHandler(final DataBufferRepository dataBufferRepository)
    {
        this.dataBufferRepository = dataBufferRepository;
    }

    @Override
    public @Nonnull Mono<ServerResponse> handle(@Nonnull ServerRequest serverRequest)
    {
        final ServerHttpRequest request = serverRequest.exchange().getRequest();
        final String requestId = request.getId();
        final HttpMethod method = request.getMethod();

        final byte[] fakeRequestLine = (method.name() + " / HTTP/1.1\r\n").getBytes(StandardCharsets.UTF_8);
        dataBufferRepository.save(ServerDirection.REQUEST, requestId, fakeRequestLine);
        dataBufferRepository.save(ServerDirection.REQUEST, requestId, extractHeaders(request));

        final Flux<Long> res = request.getBody()
                .flatMapSequential(dataBuffer ->
                {
                    final byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    dataBufferRepository.save(ServerDirection.REQUEST, requestId, bytes);
                    return Mono.just((long) bytes.length);
                });

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body(res.reduce(0L, Long::sum), Long.class);
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
