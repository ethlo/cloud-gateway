package com.ethlo.http.netty;

import java.lang.reflect.Field;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.ethlo.http.logger.CaptureConfiguration;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;

@Component
@RefreshScope
@ConditionalOnProperty("http-logging.capture.enabled")
public class LoggerHttpClientCustomizer implements HttpClientCustomizer
{
    private static final Field field = Objects.requireNonNull(ReflectionUtils.findField(HttpClientConfig.class, "loggingHandler"));

    private final boolean captureEnabled;
    private final DataBufferRepository dataBufferRepository;

    public LoggerHttpClientCustomizer(final CaptureConfiguration captureConfiguration, final DataBufferRepository dataBufferRepository)
    {
        this.captureEnabled = captureConfiguration.isEnabled();
        this.dataBufferRepository = dataBufferRepository;
    }

    @Override
    public HttpClient customize(final HttpClient httpClient)
    {
        if (captureEnabled)
        {
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, httpClient.configuration(), new HttpRequestResponseLogger(dataBufferRepository));
        }
        return httpClient;
    }
}
