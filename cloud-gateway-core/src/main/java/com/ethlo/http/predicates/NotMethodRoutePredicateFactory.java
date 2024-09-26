package com.ethlo.http.predicates;

import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class NotMethodRoutePredicateFactory extends MethodRoutePredicateFactory
{
    @Override
    public Predicate<ServerWebExchange> apply(final Config config)
    {
        return super.apply(config).negate();
    }
}