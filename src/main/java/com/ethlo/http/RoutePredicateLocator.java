package com.ethlo.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.server.ServerWebExchange;

public class RoutePredicateLocator
{
    private static final Logger logger = LoggerFactory.getLogger(RoutePredicateLocator.class);
    private final Map<String, RoutePredicateFactory<?>> predicateFactories = new LinkedHashMap<>();
    private final ConfigurationService configurationService;

    public RoutePredicateLocator(final List<RoutePredicateFactory> predicateFactories, final ConfigurationService configurationService)
    {
        this.configurationService = configurationService;
        initFactories(predicateFactories);
    }

    public AsyncPredicate<?> getPredicates(List<PredicateDefinition> predicates)
    {
        return combinePredicates(predicates);
    }

    private void initFactories(List<RoutePredicateFactory> predicateFactories)
    {
        predicateFactories.forEach(factory ->
        {
            String key = factory.name();
            if (this.predicateFactories.containsKey(key))
            {
                logger.warn("A RoutePredicateFactory named {} already exists, class: {}. It will be overwritten.", key, this.predicateFactories.get(key));
            }
            this.predicateFactories.put(key, factory);
        });
    }

    private AsyncPredicate<ServerWebExchange> combinePredicates(List<PredicateDefinition> predicates)
    {
        AsyncPredicate<ServerWebExchange> predicate = lookup(predicates.get(0));
        for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size()))
        {
            AsyncPredicate<ServerWebExchange> found = lookup(andPredicate);
            predicate = predicate.and(found);
        }

        return predicate;
    }

    private AsyncPredicate<ServerWebExchange> lookup(PredicateDefinition predicate)
    {
        final RoutePredicateFactory factory = this.predicateFactories.get(predicate.getName());
        if (factory == null)
        {
            throw new IllegalArgumentException("Unable to find RoutePredicateFactory with name " + predicate.getName());
        }

        Object config = this.configurationService.with(factory)
                .name(predicate.getName())
                .properties(predicate.getArgs())
                .eventFunction(new BiFunction<Object, Map<String, Object>, ApplicationEvent>()
                {
                    @Override
                    public ApplicationEvent apply(final Object bound, final Map<String, Object> properties)
                    {
                        return new PredicateArgsEvent(RoutePredicateLocator.this, predicate.getName(), properties);
                    }
                })
                .bind();

        return factory.applyAsync(config);
    }
}
