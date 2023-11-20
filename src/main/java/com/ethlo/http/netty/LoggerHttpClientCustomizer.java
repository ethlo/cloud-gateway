package com.ethlo.http.netty;

import java.lang.reflect.Field;
import java.util.Objects;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.util.ReflectionUtils;

import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;

public class LoggerHttpClientCustomizer implements HttpClientCustomizer
{
    private final DataBufferRepository dataBufferRepository;

    public LoggerHttpClientCustomizer(final DataBufferRepository dataBufferRepository)
    {
        this.dataBufferRepository = dataBufferRepository;
    }

    @Override
    public HttpClient customize(final HttpClient httpClient)
    {
        final Field field = Objects.requireNonNull(ReflectionUtils.findField(HttpClientConfig.class, "loggingHandler"));
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, httpClient.configuration(), new HttpRequestResponseLogger(dataBufferRepository));
        return httpClient;
    }
}
