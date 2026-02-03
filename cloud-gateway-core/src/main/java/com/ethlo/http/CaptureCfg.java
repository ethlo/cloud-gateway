package com.ethlo.http;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ethlo.http.configuration.HttpLoggingConfiguration;

@Configuration
@RefreshScope
@ConditionalOnProperty("http-logging.capture.enabled")
public class CaptureCfg
{
    @Bean
    public DataBufferRepository pooledFileDataBufferRepository(HttpLoggingConfiguration httpLoggingConfiguration) throws IOException
    {
        return new DataBufferRepository(httpLoggingConfiguration.getCapture(), httpLoggingConfiguration.maxMemoryBuffer());
    }
}