package com.ethlo.http.configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.logger.LogFilter;
import com.ethlo.http.match.RequestMatchingProcessor;
import jakarta.validation.Valid;

@Validated
@RefreshScope
@ConfigurationProperties(prefix = "http-logging")
public class HttpLoggingConfiguration
{
    public static final Integer DEFAULT_QUEUE_SIZE = 20;
    public static final Integer DEFAULT_THREAD_COUNT = 5;

    private LogFilter filter;
    private Map<String, Map<String, Object>> providers;
    @Valid
    private List<RequestMatchingProcessor> matchers;
    private Integer maxIoThreads;
    private Integer maxQueueSize;

    public LogFilter getFilter()
    {
        return filter;
    }

    public HttpLoggingConfiguration setFilter(final LogFilter filter)
    {
        this.filter = Optional.ofNullable(filter).orElse(new LogFilter());
        return this;
    }

    public Map<String, Map<String, Object>> getProviders()
    {
        return providers;
    }

    public HttpLoggingConfiguration setProviders(final Map<String, Map<String, Object>> providers)
    {
        this.providers = providers;
        return this;
    }

    public List<RequestMatchingProcessor> getMatchers()
    {
        return Optional.ofNullable(matchers).orElse(Collections.emptyList());
    }

    public void setMatchers(final List<RequestMatchingProcessor> matchers)
    {
        this.matchers = matchers;
    }

    public Integer getMaxIoThreads()
    {
        return maxIoThreads;
    }

    public void setMaxIoThreads(final Integer maxIoThreads)
    {
        this.maxIoThreads = maxIoThreads;
    }

    public Integer getMaxQueueSize()
    {
        return maxQueueSize;
    }

    public void setMaxQueueSize(final Integer maxQueueSize)
    {
        this.maxQueueSize = maxQueueSize;
    }
}