package com.ethlo.http.match;

import static com.ethlo.http.match.HeaderProcessing.DELETE;
import static com.ethlo.http.match.HeaderProcessing.NONE;
import static com.ethlo.http.match.HeaderProcessing.REDACT;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HeaderPredicate implements Function<String, HeaderProcessing>
{
    public static final HeaderPredicate ALL = new HeaderPredicate(Set.of(), Set.of());

    private Map<String, HeaderProcessing> includes;
    private Map<String, HeaderProcessing> excludes;

    public HeaderPredicate(Set<String> includes, Set<String> excludes)
    {
        setIncludes(includes);
        setExcludes(excludes);
    }

    @Override
    public HeaderProcessing apply(final String s)
    {
        for (Map.Entry<String, HeaderProcessing> include : includes.entrySet())
        {
            if (include.getKey().equalsIgnoreCase(s))
            {
                return include.getValue();
            }
        }
        if (!includes.isEmpty())
        {
            return DELETE;
        }

        for (Map.Entry<String, HeaderProcessing> exclude : excludes.entrySet())
        {
            if (exclude.getKey().equalsIgnoreCase(s))
            {
                return exclude.getValue();
            }
        }

        return NONE;
    }

    private Map.Entry<String, HeaderProcessing> parseProcessing(String line, HeaderProcessing defaultProcessing)
    {
        final String[] parts = line.splitWithDelimiters(",", 2);
        if (parts.length == 3)
        {
            final String headerName = parts[0];
            final String s = parts[2].toLowerCase();
            final HeaderProcessing headerProcessing = switch (s)
            {
                case "r" -> REDACT;
                case "d" -> DELETE;
                default ->
                        throw new IllegalArgumentException("Unknown processing instruction: " + s + " Expected one of 'd' for delete or 'r' for redact.");
            };
            return new AbstractMap.SimpleEntry<>(headerName, headerProcessing);
        }
        return new AbstractMap.SimpleEntry<>(line, defaultProcessing);
    }

    @Override
    public String toString()
    {
        return (includes.isEmpty() ? "" : "includes=" + includes) +
                (excludes.isEmpty() ? "" : ((includes.isEmpty() ? "" : ", ") + "excludes=" + excludes));
    }

    public Set<String> getIncludes()
    {
        return toString(includes);
    }

    private void setIncludes(Set<String> includes)
    {
        this.includes = Optional.ofNullable(includes).orElse(Set.of()).stream()
                .collect(Collectors.toMap(e -> parseProcessing(e, NONE).getKey(), e -> parseProcessing(e, NONE).getValue()));
    }

    public Set<String> getExcludes()
    {
        return toString(excludes);
    }

    public void setExcludes(final Set<String> excludes)
    {
        this.excludes = Optional.ofNullable(excludes).orElse(Set.of()).stream()
                .collect(Collectors.toMap(e -> parseProcessing(e, DELETE).getKey(), e -> parseProcessing(e, DELETE).getValue()));
    }

    private Set<String> toString(final Map<String, HeaderProcessing> map)
    {
        return map.entrySet().stream().map(e -> e.getValue().getId().isEmpty() ? e.getKey() : e.getKey() + "," + e.getValue().getId()).collect(Collectors.toSet());
    }
}