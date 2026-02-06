package com.ethlo.http.match;

import java.util.Map;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Configuration record for matching requests for logging
 */
@Valid
public record RequestMatchingProcessor(
        @NotEmpty String id,
        @NotEmpty @Valid Map<String, Object> predicates,
        LogOptions request,
        LogOptions response)
{
    public RequestMatchingProcessor(
            @NotEmpty final String id,
            final @NotEmpty @Valid Map<String, Object> predicates,
            final LogOptions request,
            final LogOptions response)
    {
        this.id = id;
        this.predicates = predicates;
        this.request = Optional.ofNullable(request).orElse(new LogOptions(null, null, null));
        this.response = Optional.ofNullable(response).orElse(new LogOptions(null, null, null));
    }
}