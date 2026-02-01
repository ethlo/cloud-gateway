package com.ethlo.http;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ethlo.http.blocking.configuration.HttpLoggingConfiguration;

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

    // TODO: Determine if we still need the circuitbreakerhandler
    /*
    @Bean
    public RouterFunction<ServerResponse> loggingRoutes(CircuitBreakerHandler circuitBreakerHandler)
    {
        // Standard MVC RouterFunctions
        return RouterFunctions.route()
                .path("/upstream-down", builder -> builder
                        .route(RequestPredicates.all(), circuitBreakerHandler)
                )
                .build();
    }

    @Bean
    public CircuitBreakerHandler circuitBreakerHandler(DataBufferRepository dataBufferRepository)
    {
        // Now using standard blocking handler logic
        return new CircuitBreakerHandler(dataBufferRepository);
    }
     */
}