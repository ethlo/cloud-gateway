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
import com.ethlo.http.processors.BasicAuthorizationConfig;
import com.ethlo.http.processors.JwtAuthorizationConfig;
import com.ethlo.http.processors.auth.BasicAuthorizationExtractor;
import com.ethlo.http.processors.auth.JwtAuthorizationExtractor;

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
}
