package com.ethlo.http.predicates;

import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class NotPathRoutePredicateFactory extends PathRoutePredicateFactory
{
    public NotPathRoutePredicateFactory(final WebFluxProperties webFluxProperties)
    {
        super(webFluxProperties);
    }

    @NotNull
    @Override
    public Predicate<ServerWebExchange> apply(@NotNull final Config config)
    {
        return super.apply(config).negate();
    }
}