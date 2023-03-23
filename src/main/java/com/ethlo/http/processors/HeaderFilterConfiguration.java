package com.ethlo.http.processors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

@ConfigurationProperties(prefix = "logging.filter.headers")
public class HeaderFilterConfiguration
{
    private final HeaderFilter request;
    private final HeaderFilter response;

    public HeaderFilterConfiguration(final HeaderFilter request, final HeaderFilter response)
    {
        this.request = request;
        this.response = response;
    }

    public HeaderFilter getRequest()
    {
        return Optional.ofNullable(request).orElse(HeaderFilter.EMPTY);
    }

    public HeaderFilter getResponse()
    {
        return Optional.ofNullable(response).orElse(HeaderFilter.EMPTY);
    }
}
