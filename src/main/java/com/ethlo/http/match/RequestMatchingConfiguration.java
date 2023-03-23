package com.ethlo.http.match;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

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

    public Optional<RequestPattern> matches(ServerHttpRequest request)
    {
        final Optional<RequestPattern> matchInclude = matches(includes, request);
        final boolean matchesInclude = includes.isEmpty() || matchInclude.isPresent();
        final boolean matchesExclude = !excludes.isEmpty() && matches(excludes, request).isPresent();
        if (matchesInclude && !matchesExclude)
        {
            return matchInclude;
        }
        return Optional.empty();
    }

    private Optional<RequestPattern> matches(List<RequestPattern> requestPatterns, ServerHttpRequest request)
    {
        for (final RequestPattern requestPattern : requestPatterns)
        {
            if (requestPattern.matches(request))
            {
                return Optional.of(requestPattern);
            }
        }
        return Optional.empty();
    }
}
