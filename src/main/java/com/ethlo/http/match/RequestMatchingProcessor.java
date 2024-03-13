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
        LogOptions request,
        LogOptions response)
{
    public RequestMatchingProcessor(@NotEmpty final String id, @NotEmpty
    @Valid final
    List<PredicateDefinition> predicates, final LogOptions request, final LogOptions response)
    {
        this.id = id;
        this.predicates = predicates;
        this.request = Optional.ofNullable(request).orElse(new LogOptions(null, null, null));
        this.response = Optional.ofNullable(response).orElse(new LogOptions(null, null, null));
    }
}