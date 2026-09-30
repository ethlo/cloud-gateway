package com.ethlo.http.logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ethlo.http.match.HeaderProcessing;
import com.ethlo.http.match.QueryParamPredicate;

/**
 * Query strings frequently carry sensitive data (API keys, tokens, session identifiers), so unlike headers, which
 * default to being logged as-is, query parameters are hidden by default. Only parameter names explicitly added to
 * the accept-list (the predicate's includes) are logged; anything else is removed unless the predicate itself
 * requests {@link HeaderProcessing#REDACT} for that name.
 */
public class QueryStringFilter
{
    private QueryStringFilter()
    {
    }

    /**
     * Parses a raw query string into a name-to-values map (preserving all values for repeated parameter names in
     * their encounter order), applying the accept-list/redact semantics of the given predicate.
     */
    public static Map<String, List<String>> parse(final String rawQuery, final QueryParamPredicate predicate)
    {
        final Map<String, List<String>> result = new LinkedHashMap<>();

        // No accept-list configured at all: keep query-string logging off by default.
        if (rawQuery == null || rawQuery.isEmpty() || predicate == null || predicate.getIncludes().isEmpty())
        {
            return result;
        }

        for (final String pair : rawQuery.split("&"))
        {
            if (pair.isEmpty())
            {
                continue;
            }

            final int idx = pair.indexOf('=');
            final String name = idx >= 0 ? pair.substring(0, idx) : pair;
            final String value = idx >= 0 ? pair.substring(idx + 1) : null;

            final HeaderProcessing processing = predicate.apply(name);
            final String rendered = switch (processing)
            {
                case DELETE -> null;
                case REDACT -> value != null ? RedactUtil.redact(value) : "";
                case NONE -> value != null ? value : "";
            };

            if (rendered != null)
            {
                result.computeIfAbsent(name, k -> new ArrayList<>()).add(rendered);
            }
        }
        return result;
    }
}
