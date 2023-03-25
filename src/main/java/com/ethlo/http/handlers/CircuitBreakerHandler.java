package com.ethlo.http.handlers;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.util.HttpMessageUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CircuitBreakerHandler implements HandlerFunction<ServerResponse>
{
    private final DataBufferRepository dataBufferRepository;

    public CircuitBreakerHandler(final DataBufferRepository dataBufferRepository)
    {
        this.dataBufferRepository = dataBufferRepository;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request)
    {
        // Write preamble to find body correctly
        dataBufferRepository.save(DataBufferRepository.Operation.REQUEST, request.exchange().getRequest().getId(), HttpMessageUtil.BODY_SEPARATOR);

        final Flux<Integer> res = request.bodyToFlux(DataBuffer.class)
                .flatMap(dataBuffer ->
                {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    dataBufferRepository.save(DataBufferRepository.Operation.REQUEST, request.exchange().getRequest().getId(), bytes);
                    return Mono.empty();
                });

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body(BodyInserters.fromPublisher(res, Integer.class));
    }
}
