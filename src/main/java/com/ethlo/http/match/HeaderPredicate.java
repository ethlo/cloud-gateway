package com.ethlo.http.match;

import java.util.List;
import java.util.function.Predicate;

public record HeaderPredicate(List<String> includes, List<String> excludes) implements Predicate<String>
{
    public static final HeaderPredicate ALL = new HeaderPredicate(List.of(), List.of());

    @Override
    public boolean test(final String s)
    {
        boolean match = includes == null || includes().isEmpty();
        if (includes != null)
        {
            for (String include : includes)
            {
                if (include.equalsIgnoreCase(s))
                {
                    match = true;
                    break;
                }
            }
        }

        if (excludes != null)
        {
            for (String exclude : excludes)
            {
                if (exclude.equalsIgnoreCase(s))
                {
                    match = false;
                    break;
                }
            }
        }

        return match;
    }
}