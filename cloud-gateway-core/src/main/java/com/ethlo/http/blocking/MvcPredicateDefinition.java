package com.ethlo.http.blocking;

import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;

public record MvcPredicateDefinition(String name, Map<String, String> args)
{

    /**
     * Converts the definition into a standard Spring MVC RequestPredicate
     */
    public RequestPredicate toRequestPredicate()
    {
        return switch (name.toLowerCase())
        {
            case "path" -> RequestPredicates.path(args.get("pattern"));
            case "method" -> RequestPredicates.methods(HttpMethod.valueOf(args.get("method").toUpperCase()));
            case "header" -> RequestPredicates.headers(h ->
            {
                final String val = h.firstHeader(args.get("header"));
                return val != null && val.matches(args.get("regexp"));
            });
            case "host" -> RequestPredicates.headers(h ->
            {
                String host = h.firstHeader("Host");
                return host != null && host.matches(args.get("regexp"));
            });
            default -> throw new IllegalArgumentException("Unsupported MVC predicate: " + name);
        };
    }
}