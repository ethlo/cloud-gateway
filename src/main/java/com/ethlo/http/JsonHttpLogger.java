package com.ethlo.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JsonHttpLogger implements HttpLogger
{
    private final ObjectMapper objectMapper;

    public JsonHttpLogger(final ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Override
    public void completed(final ServerHttpRequest request, final ServerHttpResponse response, final InputStream requestData, final InputStream responseData)
    {
        try
        {
            System.out.println("RequestHeaders=" + objectMapper.writeValueAsString(request.getHeaders()));
            System.out.println("RequestBody=" + new String(StreamUtils.copyToByteArray(requestData)));

            System.out.println("ResponseHeaders=" + objectMapper.writeValueAsString(response.getHeaders()));
            System.out.println("ResponseBody=" + new String(StreamUtils.copyToByteArray(responseData)));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
