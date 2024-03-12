package com.ethlo.http.configuration;

import java.util.Map;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

@Component
public class MapPropertiesRouteDefinitionLocator implements RouteDefinitionLocator
{
    private final UpstreamServiceProperties upstreamServiceProperties;

    public MapPropertiesRouteDefinitionLocator(UpstreamServiceProperties upstreamServiceProperties)
    {
        this.upstreamServiceProperties = upstreamServiceProperties;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions()
    {
        return Flux.fromIterable(upstreamServiceProperties.getRoutes().entrySet()).map(this::processEntry);
    }

    private RouteDefinition processEntry(Map.Entry<String, RouteDefinition> entry)
    {
        final RouteDefinition route = entry.getValue();
        // ensure the route has an ID.
        if (route.getId() == null)
        {
            route.setId(entry.getKey());
        }
        return route;
    }
}