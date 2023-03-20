package com.ethlo.http.match;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payload-logging.filter")
public class RequestMatchingConfiguration
{
    private final List<RequestPattern> includes;
    private final List<RequestPattern> excludes;

    public RequestMatchingConfiguration(final List<RequestPattern> includes, final List<RequestPattern> excludes)
    {
        this.includes = includes;
        this.excludes = excludes;
    }

    public List<RequestPattern> getIncludes()
    {
        return includes;
    }

    public List<RequestPattern> getExcludes()
    {
        return excludes;
    }

    public boolean matches(ServerHttpRequest request)
    {
        final boolean matchesInclude = includes.isEmpty() || matches(includes, request);
        final boolean matchesExclude = !excludes.isEmpty() && matches(excludes, request);
        return matchesInclude && !matchesExclude;
    }

    private boolean matches(List<RequestPattern> requestPatterns, ServerHttpRequest request)
    {
        for (final RequestPattern requestPattern : requestPatterns)
        {
            if (requestPattern.matches(request))
            {
                return true;
            }
        }
        return false;
    }
}
