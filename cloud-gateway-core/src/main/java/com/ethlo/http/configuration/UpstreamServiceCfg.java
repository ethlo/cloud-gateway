package com.ethlo.http.configuration;

import java.util.Map;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UpstreamServiceCfg
{
    @Bean
    @RefreshScope
    public UpstreamServiceProperties upstreamServiceProperties(HttpUpstreamServicesUpdateService httpUpstreamServicesUpdateService)
    {
        final Map.Entry<Map<String, RouteDefinition>, Boolean> data = httpUpstreamServicesUpdateService.updateAll();
        return new UpstreamServiceProperties(data.getKey());
    }
}
