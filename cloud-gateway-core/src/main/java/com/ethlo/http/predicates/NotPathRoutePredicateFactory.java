package com.ethlo.http.predicates;

import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class NotPathRoutePredicateFactory extends PathRoutePredicateFactory
{
    @Override
    public Predicate<ServerWebExchange> apply(final Config config)
    {
        return super.apply(config).negate();
    }
}