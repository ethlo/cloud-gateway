package com.ethlo.http;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.handlers.CircuitBreakerHandler;
import com.ethlo.http.processors.auth.extractors.BasicAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.BasicAuthorizationExtractor;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationExtractor;
import com.ethlo.http.processors.auth.extractors.ResponseAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.ResponseAuthorizationExtractor;

@Configuration
public class Cfg
{
    @Bean
    RouterFunction<ServerResponse> routes(CircuitBreakerHandler circuitBreakerHandler)
    {
        return nest(path("/upstream-down"), route(RequestPredicates.all(), circuitBreakerHandler));
    }

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
}
