package com.ethlo.http.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

@Validated
@RefreshScope
@ConfigurationProperties(prefix = "upstream")
public class HttpUpstreamServicesConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(HttpUpstreamServicesConfiguration.class);
    @Valid
    private final List<UpstreamService> services;

    public HttpUpstreamServicesConfiguration(final List<UpstreamService> services)
    {
        this.services = services;
    }

    public List<UpstreamService> getServices()
    {
        return services;
    }
}