package com.ethlo.http.match;

import java.util.List;
import java.util.Optional;

import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@Valid
public record RequestMatchingProcessor(
        @NotEmpty String id,
        @NotEmpty
        @Valid
        List<PredicateDefinition> predicates,
        Log request,
        Log response)
{
    public RequestMatchingProcessor(@NotEmpty final String id, @NotEmpty
    @Valid final
    List<PredicateDefinition> predicates, final Log request, final Log response)
    {
        this.id = id;
        this.predicates = predicates;
        this.request = Optional.ofNullable(request).orElse(new Log(null, null));
        this.response = Optional.ofNullable(response).orElse(new Log(null, null));
    }

    public record Log(Boolean headers, Boolean body)
    {
        public Log(final Boolean headers, final Boolean body)
        {
            this.headers = Optional.ofNullable(headers).orElse(true);
            this.body = Optional.ofNullable(body).orElse(false);
        }
    }
}