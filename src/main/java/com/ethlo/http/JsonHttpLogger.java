package com.ethlo.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JsonHttpLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(JsonHttpLogger.class);

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
            if (logger.isInfoEnabled())
            {
                logger.info("RequestHeaders={}", objectMapper.writeValueAsString(request.getHeaders()));
                logger.info("RequestBody={}", new String(StreamUtils.copyToByteArray(requestData)));
                logger.info("ResponseHeaders={}", objectMapper.writeValueAsString(response.getHeaders()));
                logger.info("ResponseBody={}", new String(StreamUtils.copyToByteArray(responseData)));
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
