package com.ethlo.http.configuration;

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Valid
@Validated
public record UpstreamService(@NotNull String name, @NotNull URI configUrl) implements Comparable<UpstreamService>
{
    @Override
    public int compareTo(@NotNull final UpstreamService upstreamService)
    {
        return name.compareTo(upstreamService.name);
    }
}