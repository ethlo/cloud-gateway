package com.ethlo.http.match;

import static com.ethlo.http.match.HeaderProcessing.DELETE;
import static com.ethlo.http.match.HeaderProcessing.NONE;
import static com.ethlo.http.match.HeaderProcessing.REDACT;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An include-only accept-list, matching names case-sensitively (unlike {@link HeaderPredicate}). Query parameter
 * names are case-sensitive per the URI spec, so an accept-list entry for "page" must not also match "PAGE" or
 * "Page".
 * <p>
 * Query logging is deliberately allow-list-only: a name not in the list is always {@link HeaderProcessing#DELETE},
 * so there is no separate exclude list to configure.
 */
public class QueryParamPredicate implements Function<String, HeaderProcessing>
{
    private Map<String, HeaderProcessing> includes;

    public QueryParamPredicate(Set<String> includes)
    {
        setIncludes(includes);
    }

    @Override
    public HeaderProcessing apply(final String s)
    {
        return includes.getOrDefault(s, DELETE);
    }

    private Map.Entry<String, HeaderProcessing> parseProcessing(String line, HeaderProcessing defaultProcessing)
    {
        final String[] parts = line.splitWithDelimiters(",", 2);
        if (parts.length == 3)
        {
            final String paramName = parts[0];
            final String s = parts[2].toLowerCase();
            final HeaderProcessing processing = switch (s)
            {
                case "r" -> REDACT;
                case "d" -> DELETE;
                default ->
                        throw new IllegalArgumentException("Unknown processing instruction: " + s + " Expected one of 'd' for delete or 'r' for redact.");
            };
            return new AbstractMap.SimpleEntry<>(paramName, processing);
        }
        return new AbstractMap.SimpleEntry<>(line, defaultProcessing);
    }

    @Override
    public String toString()
    {
        return includes.isEmpty() ? "" : "includes=" + includes;
    }

    public Set<String> getIncludes()
    {
        return includes.entrySet().stream()
                .map(e -> e.getValue().getId().isEmpty() ? e.getKey() : e.getKey() + "," + e.getValue().getId())
                .collect(Collectors.toSet());
    }

    private void setIncludes(Set<String> includes)
    {
        final Map<String, HeaderProcessing> result = new TreeMap<>();
        Optional.ofNullable(includes).orElse(Set.of()).forEach(line ->
        {
            final Map.Entry<String, HeaderProcessing> parsed = parseProcessing(line, NONE);
            result.put(parsed.getKey(), parsed.getValue());
        });
        this.includes = result;
    }

    public Map<String, HeaderProcessing> getIncludeProcessing()
    {
        return includes;
    }
}
