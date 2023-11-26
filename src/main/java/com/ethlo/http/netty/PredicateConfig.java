package com.ethlo.http.netty;

import org.springframework.cloud.gateway.handler.AsyncPredicate;

import com.ethlo.http.match.RequestMatchingProcessor;

public record PredicateConfig(String id,
                              AsyncPredicate predicate,
                              RequestMatchingProcessor.Log request,
                              RequestMatchingProcessor.Log response)
{
}
