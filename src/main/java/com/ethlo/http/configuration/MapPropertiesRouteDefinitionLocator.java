package com.ethlo.http.configuration;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.util.Map;

@Component
public class MapPropertiesRouteDefinitionLocator implements RouteDefinitionLocator
{
	private final UpstreamServiceProperties properties;

	public MapPropertiesRouteDefinitionLocator(UpstreamServiceProperties properties) {
		this.properties = properties;
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return Flux.fromIterable(properties.getRoutes().entrySet()).map(this::processEntry);
	}

	private RouteDefinition processEntry(Map.Entry<String, RouteDefinition> entry) {
		final RouteDefinition route = entry.getValue();
		// ensure the route has an ID.
		if (route.getId() == null) {
			route.setId(entry.getKey());
		}
		return route;
	}
}