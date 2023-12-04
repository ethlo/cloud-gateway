package com.ethlo.http.configuration;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@RefreshScope
@ConfigurationProperties(prefix = "upstream")
public class UpstreamServiceConfiguration
{
    private List<UpstreamService> services;

    public List<UpstreamService> getServices()
    {
        return Optional.ofNullable(services).orElse(List.of());
    }

    public void setServices(final List<UpstreamService> services)
    {
        this.services = services;
    }
}
