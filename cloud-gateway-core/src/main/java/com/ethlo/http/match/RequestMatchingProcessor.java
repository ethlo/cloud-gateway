package com.ethlo.http.match;

import java.util.List;
import java.util.Optional;

import com.ethlo.http.MvcPredicateDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Configuration record for matching requests in the MVC Gateway.
 * Predicates are now matched against HttpServletRequest.
 */
@Valid
public record RequestMatchingProcessor(
        @NotEmpty String id,
        @NotEmpty @Valid List<MvcPredicateDefinition> predicates,
        LogOptions request,
        LogOptions response)
{
    public RequestMatchingProcessor(
            @NotEmpty final String id,
            @NotEmpty @Valid final List<MvcPredicateDefinition> predicates,
            final LogOptions request,
            final LogOptions response)
    {
        this.id = id;
        this.predicates = predicates;
        this.request = Optional.ofNullable(request).orElse(new LogOptions(null, null, null));
        this.response = Optional.ofNullable(response).orElse(new LogOptions(null, null, null));
    }
}