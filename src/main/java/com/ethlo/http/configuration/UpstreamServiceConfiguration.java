package com.ethlo.http.configuration;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upstream")
public class UpstreamServiceConfiguration
{
    private final List<UpstreamService> services;

    public UpstreamServiceConfiguration(final List<UpstreamService> services)
    {
        this.services = services;
    }

    public List<UpstreamService> getServices()
    {
        return Optional.ofNullable(services).orElse(List.of());
    }
}
