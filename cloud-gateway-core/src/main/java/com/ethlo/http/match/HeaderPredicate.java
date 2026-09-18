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

    private HeaderPredicate(Map<String, HeaderProcessing> includes, Map<String, HeaderProcessing> excludes)
    {
        this.includes = caseInsensitive(includes);
        this.excludes = caseInsensitive(excludes);
    }

    /**
     * Creates a predicate from already parsed header names and their processing instruction.
     */
    public static HeaderPredicate of(Map<String, HeaderProcessing> includes, Map<String, HeaderProcessing> excludes)
    {
        return new HeaderPredicate(includes, excludes);
    }

    private static Map<String, HeaderProcessing> caseInsensitive(Map<String, HeaderProcessing> source)
    {
        final Map<String, HeaderProcessing> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        result.putAll(source);
        return result;
    }

    @Override
    public HeaderProcessing apply(final String s)
    {
        final HeaderProcessing include = includes.get(s);
        if (include != null)
        {
            return include;
        }
        if (!includes.isEmpty())
        {
            return DELETE;
        }

        return excludes.getOrDefault(s, NONE);
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
        this.includes = parseAll(includes, NONE);
    }

    public Set<String> getExcludes()
    {
        return toString(excludes);
    }

    public void setExcludes(final Set<String> excludes)
    {
        this.excludes = parseAll(excludes, DELETE);
    }

    private Map<String, HeaderProcessing> parseAll(final Set<String> lines, final HeaderProcessing defaultProcessing)
    {
        final Map<String, HeaderProcessing> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Optional.ofNullable(lines).orElse(Set.of()).forEach(line ->
        {
            final Map.Entry<String, HeaderProcessing> parsed = parseProcessing(line, defaultProcessing);
            result.put(parsed.getKey(), parsed.getValue());
        });
        return result;
    }

    public Map<String, HeaderProcessing> getIncludeProcessing()
    {
        return includes;
    }

    public Map<String, HeaderProcessing> getExcludeProcessing()
    {
        return excludes;
    }

    private Set<String> toString(final Map<String, HeaderProcessing> map)
    {
        return map.entrySet().stream().map(e -> e.getValue().getId().isEmpty() ? e.getKey() : e.getKey() + "," + e.getValue().getId()).collect(Collectors.toSet());
    }
}