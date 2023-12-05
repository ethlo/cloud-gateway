package com.ethlo.http;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.handlers.CircuitBreakerHandler;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PooledFileDataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.TagRequestIdGlobalFilter;
import com.ethlo.http.processors.LogPreProcessor;

@Configuration
@RefreshScope
@ConditionalOnProperty("http-logging.capture.enabled")
public class CaptureCfg
{
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
    @RefreshScope
    public TagRequestIdGlobalFilter tagRequestIdGlobalFilter(final HttpLogger httpLogger,
                                                             final DataBufferRepository dataBufferRepository,
                                                             final LogPreProcessor logPreProcessor,
                                                             final RoutePredicateLocator routePredicateLocator,
                                                             final HttpLoggingConfiguration httpLoggingConfiguration)
    {
        final List<PredicateConfig> predicateConfigs = httpLoggingConfiguration.getMatchers()
                .stream()
                .map(c -> new PredicateConfig(c.id(), routePredicateLocator.getPredicates(c.predicates()), c.request(), c.response()))
                .toList();
        return new TagRequestIdGlobalFilter(httpLogger, dataBufferRepository, logPreProcessor, predicateConfigs);
    }

    @Bean
    RouterFunction<ServerResponse> routes(CircuitBreakerHandler circuitBreakerHandler)
    {
        return nest(path("/upstream-down"), route(RequestPredicates.all(), circuitBreakerHandler));
    }

    @Bean
    @RefreshScope
    public RoutePredicateLocator routePredicateLocator(final List<RoutePredicateFactory> predicateFactories, final ConfigurationService configurationService)
    {
        return new RoutePredicateLocator(predicateFactories, configurationService);
    }
}
