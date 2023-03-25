package com.ethlo.http.match;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.http.server.reactive.ServerHttpRequest;

public class RequestMatchingProcessor
{
    private final List<RequestPattern> includes;
    private final List<RequestPattern> excludes;
    private final boolean logRequestBody;
    private final boolean logResponseBody;

    public RequestMatchingProcessor(final List<RequestPattern> includes, final List<RequestPattern> excludes, final boolean logRequestBody, final boolean logResponseBody)
    {
        this.includes = includes;
        this.excludes = excludes;
        this.logRequestBody = logRequestBody;
        this.logResponseBody = logResponseBody;
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

    public boolean isLogRequestBody()
    {
        return logRequestBody;
    }

    public boolean isLogResponseBody()
    {
        return logResponseBody;
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

    @Override
    public String toString()
    {
        return new StringJoiner(", ", RequestMatchingProcessor.class.getSimpleName() + "[", "]")
                .add("includes=" + includes)
                .add("excludes=" + excludes)
                .add("logRequestBody=" + logRequestBody)
                .add("logResponseBody=" + logResponseBody)
                .toString();
    }
}
