package com.ethlo.http.match;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "http-logging")
public class HttpLoggingConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(HttpLoggingConfiguration.class);
    @Valid
    private final List<RequestMatchingProcessor> matchers;

    public HttpLoggingConfiguration(final List<RequestMatchingProcessor> matchers)
    {
        this.matchers = matchers;
        logger.info("Matchers found: {}", getMatchers().size());
        getMatchers().forEach(m -> logger.info("{}", m));
    }

    public List<RequestMatchingProcessor> getMatchers()
    {
        return Optional.ofNullable(matchers).orElse(Collections.emptyList());
    }
}