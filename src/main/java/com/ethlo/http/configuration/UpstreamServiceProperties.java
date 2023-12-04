package com.ethlo.http.configuration;

import java.util.Map;

import org.springframework.cloud.gateway.route.RouteDefinition;

public class UpstreamServiceProperties
{
    private final Map<String, RouteDefinition> routes;

    public UpstreamServiceProperties(final Map<String, RouteDefinition> routes)
    {
        this.routes = routes;
    }

    public Map<String, RouteDefinition> getRoutes()
    {
        return routes;
    }
}