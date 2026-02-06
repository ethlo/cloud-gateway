package com.ethlo.http.netty;

import java.util.function.Predicate;

import com.ethlo.http.match.LogOptions;
import jakarta.servlet.http.HttpServletRequest;

public record PredicateConfig(String id,
                              Predicate<HttpServletRequest> predicate,
                              LogOptions request,
                              LogOptions response)
{

}
