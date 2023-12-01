package com.ethlo.http.configuration;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.match.RequestMatchingProcessor;
import jakarta.validation.Valid;

@Validated
@RefreshScope
@ConfigurationProperties(prefix = "http-logging")
public class HttpLoggingConfiguration
{
    @Valid
    private List<RequestMatchingProcessor> matchers;

    public List<RequestMatchingProcessor> getMatchers()
    {
        return Optional.ofNullable(matchers).orElse(Collections.emptyList());
    }

    public void setMatchers(final List<RequestMatchingProcessor> matchers)
    {
        this.matchers = matchers;
    }
}