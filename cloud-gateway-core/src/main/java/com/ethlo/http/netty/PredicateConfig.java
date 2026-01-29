package com.ethlo.http.netty;

import com.ethlo.http.match.LogOptions;

import org.springframework.cloud.gateway.handler.AsyncPredicate;

public record PredicateConfig(String id,
                              AsyncPredicate predicate,
                              LogOptions request,
                              LogOptions response)
{

}
