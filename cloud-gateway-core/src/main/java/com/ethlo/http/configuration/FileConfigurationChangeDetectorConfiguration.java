package com.ethlo.http.configuration;

import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "config-refresh")
public record FileConfigurationChangeDetectorConfiguration(Boolean enabled, Duration interval)
{
    public Boolean enabled()
    {
        return Optional.ofNullable(enabled).orElse(true);
    }

    public Duration interval()
    {
        return Optional.ofNullable(interval).orElse(Duration.ofSeconds(10));
    }
}

