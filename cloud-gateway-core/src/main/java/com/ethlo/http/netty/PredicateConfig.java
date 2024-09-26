package com.ethlo.http.netty;

import org.springframework.cloud.gateway.handler.AsyncPredicate;

import com.ethlo.http.match.LogOptions;

public record PredicateConfig(String id,
                              AsyncPredicate predicate,
                              LogOptions request,
                              LogOptions response)
{

}
