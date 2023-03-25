package com.ethlo.http.processors;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;

public class HeaderFilter
{
    public static final HeaderFilter EMPTY = new HeaderFilter(Collections.emptySet(), Collections.emptySet());

    private final Set<String> includes;
    private final Set<String> excludes;

    public HeaderFilter(final Set<String> includes, final Set<String> excludes)
    {
        this.includes = includes.stream().map(String::toLowerCase).collect(Collectors.toSet());
        this.excludes = excludes.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    public HttpHeaders filter(HttpHeaders headers)
    {
        final HttpHeaders result = new HttpHeaders();
        headers.forEach((name, value) ->
        {
            final String lCase = name.toLowerCase();
            final boolean included = includes == null || includes.isEmpty() || includes.contains(lCase);
            final boolean excluded = excludes != null && excludes.contains(lCase);
            if (included && !excluded)
            {
                result.put(name, value);
            }
        });
        return result;
    }
}
