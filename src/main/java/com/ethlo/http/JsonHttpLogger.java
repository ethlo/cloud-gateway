package com.ethlo.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import org.springframework.util.StreamUtils;

import reactor.netty.http.client.HttpResponseDecoderSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

@Component
public class JsonHttpLogger implements HttpLogger
{
    private final ObjectMapper objectMapper;

    public JsonHttpLogger(final ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Override
    public void terminated(final ServerHttpRequest request, final ServerHttpResponse response, final InputStream requestData, final InputStream responseData)
    {
        try
        {
            System.out.println(objectMapper.writeValueAsString(request.getHeaders()));
            System.out.println(objectMapper.writeValueAsString(response.getHeaders()));
            System.out.println(new String(StreamUtils.copyToByteArray(requestData)));
            System.out.println(new String(StreamUtils.copyToByteArray(responseData)));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
