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
        final HeaderPredicate requestMerged = mergeHeader(httpLoggingConfiguration.getFilter().getHeaders(), predicateConfig.request().headers());
        final HeaderPredicate responseMerged = mergeHeader(httpLoggingConfiguration.getFilter().getHeaders(), predicateConfig.response().headers());
        return new PredicateConfig(predicateConfig.id(), predicateConfig.predicate(), new LogOptions(requestMerged, predicateConfig.request().raw(), predicateConfig.request().body()), new LogOptions(responseMerged, predicateConfig.response().raw(), predicateConfig.response().body()));
    }

    public static HeaderPredicate mergeHeader(HeaderPredicate global, HeaderPredicate local)
    {
        final Set<String> globalIncludes = new HashSet<>(global.getIncludes());
        final Set<String> globalExcludes = new HashSet<>(global.getExcludes());

        // Local includes overwrite global excludes
        globalExcludes.removeAll(local.getIncludes());
        globalExcludes.addAll(local.getExcludes());

        // Local excludes overwrite global includes
        globalIncludes.removeAll(local.getExcludes());
        globalIncludes.addAll(local.getIncludes());

        return new HeaderPredicate(globalIncludes, globalExcludes);
    }

    public PredicateConfig merge(PredicateConfig predicateConfig)
    {
        return cache.computeIfAbsent(predicateConfig.id(), (k) -> mergeFilter(httpLoggingConfiguration, predicateConfig));
    }
}
