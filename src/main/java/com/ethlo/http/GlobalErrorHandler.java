package com.ethlo.http;

import jakarta.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

@Configuration
@Order(-2)
public class GlobalErrorHandler implements ErrorWebExceptionHandler
{
    private final Logger logger = LoggerFactory.getLogger(GlobalErrorHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalErrorHandler(final ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Override
    public @Nonnull Mono<Void> handle(@Nonnull ServerWebExchange serverWebExchange, @Nonnull Throwable throwable)
    {
        if (throwable instanceof EmptyResultDataAccessException e)
        {
            return handleError(serverWebExchange, HttpStatus.NOT_FOUND, e.getMessage());
        }
        else if (throwable instanceof ResponseStatusException responseStatusException)
        {
            return handleError(serverWebExchange, HttpStatus.resolve(responseStatusException.getStatusCode().value()), responseStatusException.getMessage());
        }
        logger.warn("An unhandled exception occurred: {}", throwable.getMessage(), throwable);
        return handleError(serverWebExchange, HttpStatus.INTERNAL_SERVER_ERROR, "Unknown error");
    }

    private Mono<Void> handleError(ServerWebExchange serverWebExchange, HttpStatus status, String message)
    {
        final DataBufferFactory bufferFactory = serverWebExchange.getResponse().bufferFactory();
        final ServerHttpResponse response = serverWebExchange.getResponse();
        response.setStatusCode(status);
        DataBuffer dataBuffer;
        try
        {
            dataBuffer = bufferFactory.wrap(objectMapper.writeValueAsBytes(new HttpError(status.value(), message)));
        }
        catch (JsonProcessingException e)
        {
            dataBuffer = bufferFactory.wrap("".getBytes());
        }
        serverWebExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(dataBuffer));
    }

    public record HttpError(int status, String message)
    {

    }
}