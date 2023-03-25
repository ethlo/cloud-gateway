package com.ethlo.http.processors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

@ConfigurationProperties(prefix = "logging.filter")
public class HeaderFilterConfiguration
{
    private final HeaderFilter requestHeaders;
    private final HeaderFilter responseHeaders;

    public HeaderFilterConfiguration(final HeaderFilter requestHeaders, final HeaderFilter responseHeaders)
    {
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
    }

    public HeaderFilter getRequestHeaders()
    {
        return Optional.ofNullable(requestHeaders).orElse(HeaderFilter.EMPTY);
    }

    public HeaderFilter getResponseHeaders()
    {
        return Optional.ofNullable(responseHeaders).orElse(HeaderFilter.EMPTY);
    }
}
