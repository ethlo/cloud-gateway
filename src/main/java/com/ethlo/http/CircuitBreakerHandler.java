package com.ethlo.http;

import java.io.IOException;

import com.ethlo.http.netty.HttpMessageUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.netty.DataBufferRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CircuitBreakerHandler implements HandlerFunction<ServerResponse>
{
    private final DataBufferRepository dataBufferRepository;
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerHandler.class);

    public CircuitBreakerHandler(final DataBufferRepository dataBufferRepository)
    {
        this.dataBufferRepository = dataBufferRepository;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request)
    {
        // Write preamble to find body correctly
        dataBufferRepository.save(DataBufferRepository.Operation.REQUEST, request.exchange().getRequest().getId(), HttpMessageUtil.BODY_SEPARATOR);

        final Flux<Integer> res = request.bodyToMono(DataBuffer.class).flatMapMany(dataBuffer ->
        {
            try
            {
                dataBufferRepository.save(DataBufferRepository.Operation.REQUEST, request.exchange().getRequest().getId(), StreamUtils.copyToByteArray(dataBuffer.asInputStream(true)));
                return Mono.empty();
            }
            catch (IOException e)
            {
                return Mono.error(e);
            }
        });

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body(BodyInserters.fromPublisher(res, Integer.class));
    }
}
