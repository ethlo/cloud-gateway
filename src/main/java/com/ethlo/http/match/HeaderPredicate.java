package com.ethlo.http.match;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public record HeaderPredicate(Set<String> includes, Set<String> excludes) implements Predicate<String>
{
    public static final HeaderPredicate ALL = new HeaderPredicate(Set.of(), Set.of());

    public HeaderPredicate(Set<String> includes, Set<String> excludes)
    {
        this.includes = Optional.ofNullable(includes).orElse(Set.of());
        this.excludes = Optional.ofNullable(excludes).orElse(Set.of());

        for (String s : this.includes)
        {
            if (this.excludes.contains(s))
            {
                throw new IllegalArgumentException("Item '" + s + "' is in both includes and excludes set");
            }
        }
    }

    @Override
    public boolean test(final String s)
    {
        boolean match = includes().isEmpty();
        for (String include : includes)
        {
            if (include.equalsIgnoreCase(s))
            {
                match = true;
                break;
            }
        }

        for (String exclude : excludes)
        {
            if (exclude.equalsIgnoreCase(s))
            {
                match = false;
                break;
            }
        }

        return match;
    }

    @Override
    public String toString()
    {
        return "HeaderPredicate[" +
                "includes=" + includes + ", " +
                "excludes=" + excludes + ']';
    }

}