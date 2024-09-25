package com.ethlo.http.logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.match.HeaderPredicate;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.netty.PredicateConfig;

public class LoggingFilterService
{
    private final HttpLoggingConfiguration httpLoggingConfiguration;
    private final ConcurrentMap<String, PredicateConfig> cache = new ConcurrentHashMap<>();

    public LoggingFilterService(HttpLoggingConfiguration httpLoggingConfiguration)
    {
        this.httpLoggingConfiguration = httpLoggingConfiguration;
    }

    public static PredicateConfig mergeFilter(HttpLoggingConfiguration httpLoggingConfiguration, PredicateConfig predicateConfig)
    {
        final HeaderPredicate headerGlobal = httpLoggingConfiguration.getFilter().getHeaders();
        final HeaderPredicate headersLocal = predicateConfig.request().headers();

        final Set<String> mergedIncludes = new HashSet<>(headerGlobal.getIncludes());
        mergedIncludes.addAll(headersLocal.getIncludes());

        final Set<String> diffedExcludes = new HashSet<>(0);

        if (headersLocal.getIncludes().isEmpty())
        {
            diffedExcludes.addAll(headerGlobal.getExcludes());
        }
        else
        {
            // Add local excludes
            headerGlobal.getExcludes().forEach(mergedIncludes::remove);
            diffedExcludes.addAll(headersLocal.getExcludes());
        }

        final HeaderPredicate merged = new HeaderPredicate(mergedIncludes, diffedExcludes);
        return new PredicateConfig(predicateConfig.id(), predicateConfig.predicate(), new LogOptions(merged, predicateConfig.request().raw(), predicateConfig.request().body()), predicateConfig.response());
    }

    public PredicateConfig merge(PredicateConfig predicateConfig)
    {
        return cache.computeIfAbsent(predicateConfig.id(), (k) -> mergeFilter(httpLoggingConfiguration, predicateConfig));
    }
}
