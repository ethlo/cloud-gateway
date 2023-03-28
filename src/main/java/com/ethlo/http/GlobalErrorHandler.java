package com.ethlo.http;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

@Configuration
@Order(-2)
public class GlobalErrorHandler implements ErrorWebExceptionHandler
{
    private final ObjectMapper objectMapper;

    public GlobalErrorHandler(final ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange serverWebExchange, Throwable throwable)
    {
        if (throwable instanceof EmptyResultDataAccessException e)
        {
            return handleError(serverWebExchange, HttpStatus.NOT_FOUND, e.getMessage());
        }
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
            dataBuffer = bufferFactory.wrap(objectMapper.writeValueAsBytes(new HttpError(message)));
        }
        catch (JsonProcessingException e)
        {
            dataBuffer = bufferFactory.wrap("".getBytes());
        }
        serverWebExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(dataBuffer));
    }

    public class HttpError
    {
        private final String message;

        HttpError(String message)
        {
            this.message = message;
        }

        public String getMessage()
        {
            return message;
        }
    }
}