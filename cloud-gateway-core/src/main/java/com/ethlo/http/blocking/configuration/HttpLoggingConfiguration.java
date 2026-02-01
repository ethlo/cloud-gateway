package com.ethlo.http.blocking.configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.logger.LogFilter;
import com.ethlo.http.match.RequestMatchingProcessor;
import jakarta.validation.Valid;

@Validated
@RefreshScope
@ConfigurationProperties(prefix = "http-logging")
public class HttpLoggingConfiguration
{
    public static final DataSize DEFAULT_MAX_MEMORY_BUFFER = DataSize.ofMegabytes(1);

    private CaptureConfiguration capture;
    private LogFilter filter;
    private Map<String, Map<String, Object>> providers;
    @Valid
    private List<RequestMatchingProcessor> matchers;
    private DataSize maxMemoryBuffer = DEFAULT_MAX_MEMORY_BUFFER;

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

    public DataSize maxMemoryBuffer()
    {
        return maxMemoryBuffer;
    }

    public HttpLoggingConfiguration setMaxMemoryBuffer(final DataSize maxMemoryBuffer)
    {
        this.maxMemoryBuffer = maxMemoryBuffer;
        return this;
    }

    public CaptureConfiguration getCapture()
    {
        return capture;
    }

    public HttpLoggingConfiguration setCapture(final CaptureConfiguration capture)
    {
        this.capture = capture;
        return this;
    }
}