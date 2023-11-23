package com.ethlo.http.match;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.http.server.reactive.ServerHttpRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public class RequestMatchingProcessor
{
    @NotEmpty
    @Valid
    private final List<PredicateDefinition> predicates;
    private final boolean logRequestBody;
    private final boolean logResponseBody;

    public RequestMatchingProcessor(final @NotEmpty @Valid List<PredicateDefinition> predicates, final boolean logRequestBody, final boolean logResponseBody)
    {
        this.predicates = predicates;
        this.logRequestBody = logRequestBody;
        this.logResponseBody = logResponseBody;
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
                .add("predicates=" + predicates)
                .add("logRequestBody=" + logRequestBody)
                .add("logResponseBody=" + logResponseBody)
                .toString();
    }

    public List<PredicateDefinition> getPredicates()
    {
        return predicates;
    }
}
