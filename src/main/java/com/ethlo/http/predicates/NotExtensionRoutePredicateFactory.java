package com.ethlo.http.predicates;

import java.util.function.Predicate;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class NotExtensionRoutePredicateFactory extends ExtensionRoutePredicateFactory
{
    @Override
    public Predicate<ServerWebExchange> apply(final Config config)
    {
        return super.apply(config).negate();
    }
}