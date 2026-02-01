package com.ethlo.http.netty;

import com.ethlo.http.match.LogOptions;

public record PredicateConfig(String id,
                              java.util.function.Predicate<jakarta.servlet.http.HttpServletRequest> predicate,
                              LogOptions request,
                              LogOptions response)
{

}
