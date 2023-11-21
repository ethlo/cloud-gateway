package com.ethlo.http;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.handlers.CircuitBreakerHandler;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.match.HttpLoggingConfiguration;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.LoggerHttpClientCustomizer;
import com.ethlo.http.netty.PooledFileDataBufferRepository;
import com.ethlo.http.netty.TagRequestIdGlobalFilter;
import com.ethlo.http.processors.LogPreProcessor;
import com.ethlo.http.processors.auth.extractors.BasicAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.BasicAuthorizationExtractor;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationExtractor;
import com.ethlo.http.processors.auth.extractors.ResponseAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.ResponseAuthorizationExtractor;

@ConditionalOnProperty("http-logging.capture.enabled")
@Configuration
public class CaptureCfg
{
    @Bean
    public JwtAuthorizationExtractor jwtAuthorizationExtractor(JwtAuthorizationConfig config)
    {
        return new JwtAuthorizationExtractor(config);
    }

    @Bean
    public BasicAuthorizationExtractor basicAuthorizationExtractor(BasicAuthorizationConfig config)
    {
        return new BasicAuthorizationExtractor(config);
    }

    @Bean
    public ResponseAuthorizationExtractor responseAuthorizationExtractor(ResponseAuthorizationConfig config)
    {
        return new ResponseAuthorizationExtractor(config);
    }

    @Bean
    public LoggerHttpClientCustomizer loggerHttpClientCustomizer(DataBufferRepository dataBufferRepository)
    {
        return new LoggerHttpClientCustomizer(dataBufferRepository);
    }

    @Bean
    public DataBufferRepository pooledFileDataBufferRepository(CaptureConfiguration captureConfiguration)
    {
        return new PooledFileDataBufferRepository(captureConfiguration);
    }

    @Bean
    public CircuitBreakerHandler circuitBreakerHandler(DataBufferRepository dataBufferRepository)
    {
        return new CircuitBreakerHandler(dataBufferRepository);
    }

    @Bean
    public TagRequestIdGlobalFilter tagRequestIdGlobalFilter(final HttpLogger httpLogger, final DataBufferRepository dataBufferRepository, final HttpLoggingConfiguration httpLoggingConfiguration, final LogPreProcessor logPreProcessor)
    {
        return new TagRequestIdGlobalFilter(httpLogger, dataBufferRepository, httpLoggingConfiguration, logPreProcessor);
    }

    @Bean
    RouterFunction<ServerResponse> routes(CircuitBreakerHandler circuitBreakerHandler)
    {
        return nest(path("/upstream-down"), route(RequestPredicates.all(), circuitBreakerHandler));
    }
}