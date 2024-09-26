package com.ethlo.http.configuration;

import java.util.Map;

import org.springframework.cloud.gateway.route.RouteDefinition;

public interface HttpUpstreamServicesUpdateService
{
    Map.Entry<Map<String, RouteDefinition>, Boolean> updateAll();
}
