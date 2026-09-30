package com.ethlo.http.logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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

    public static String filter(final String rawQuery, final QueryParamPredicate predicate)
    {
        if (rawQuery == null || rawQuery.isEmpty())
        {
            return rawQuery;
        }

        final StringBuilder result = new StringBuilder();
        process(rawQuery, predicate, (name, value) ->
        {
            if (!result.isEmpty())
            {
                result.append('&');
            }
            result.append(name).append('=').append(value != null ? value : "");
        });
        return !result.isEmpty() ? result.toString() : null;
    }

    /**
     * Same accept-list/redact semantics as {@link #filter(String, QueryParamPredicate)}, but structured as a
     * name-to-values map (preserving repeated parameter names) rather than reassembled into a query string.
     */
    public static Map<String, List<String>> parse(final String rawQuery, final QueryParamPredicate predicate)
    {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty())
        {
            return result;
        }

        process(rawQuery, predicate, (name, value) -> result.computeIfAbsent(name, k -> new ArrayList<>()).add(value != null ? value : ""));
        return result;
    }

    private static void process(final String rawQuery, final QueryParamPredicate predicate, final BiConsumer<String, String> renderedConsumer)
    {
        // No accept-list configured at all: keep query-string logging off by default.
        if (predicate == null || predicate.getIncludes().isEmpty())
        {
            return;
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
            switch (processing)
            {
                case DELETE ->
                {
                    // Omit entirely
                }
                case REDACT -> renderedConsumer.accept(name, value != null ? RedactUtil.redact(value) : "");
                case NONE -> renderedConsumer.accept(name, value);
            }
        }
    }
}
