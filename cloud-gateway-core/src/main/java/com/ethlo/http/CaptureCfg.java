package com.ethlo.http;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ethlo.http.configuration.HttpLoggingConfiguration;

@Configuration
@RefreshScope
public class CaptureCfg
{
    @Bean
    @ConditionalOnProperty("http-logging.capture.enabled")
    public DataBufferRepository dataBufferRepository(HttpLoggingConfiguration httpLoggingConfiguration) throws IOException
    {
        return new DefaultDataBufferRepository(httpLoggingConfiguration.getCapture(), httpLoggingConfiguration.maxMemoryBuffer());
    }

    @Bean
    @ConditionalOnProperty(value = "http-logging.capture.enabled", havingValue = "false", matchIfMissing = true)
    public DataBufferRepository nopDataBufferRepository()
    {
        return NopDataBufferRepository.INSTANCE;
    }
}