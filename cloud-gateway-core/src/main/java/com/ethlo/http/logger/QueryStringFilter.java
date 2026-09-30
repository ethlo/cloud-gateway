package com.ethlo.http.logger;

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

        // No accept-list configured at all: keep query-string logging off by default.
        if (predicate == null || predicate.getIncludes().isEmpty())
        {
            return null;
        }

        final StringBuilder result = new StringBuilder();
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
                case REDACT -> name + "=" + (value != null ? RedactUtil.redact(value) : "");
                case NONE -> pair;
            };

            if (rendered != null)
            {
                if (!result.isEmpty())
                {
                    result.append('&');
                }
                result.append(rendered);
            }
        }
        return !result.isEmpty() ? result.toString() : null;
    }
}
