package com.ethlo.http.netty;

import org.springframework.cloud.gateway.handler.AsyncPredicate;

public record PredicateConfig(AsyncPredicate predicate, boolean logRequest, boolean logResponse)
{
}
