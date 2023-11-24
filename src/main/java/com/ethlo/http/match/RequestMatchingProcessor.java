package com.ethlo.http.match;

import java.util.List;

import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@Valid
public record RequestMatchingProcessor(
        @NotEmpty String id,
        @NotEmpty
        @Valid
        List<PredicateDefinition> predicates,
        boolean logRequestBody,
        boolean logResponseBody)
{
}

