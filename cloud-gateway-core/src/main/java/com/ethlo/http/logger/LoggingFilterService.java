package com.ethlo.http.logger;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.match.HeaderPredicate;
import com.ethlo.http.match.HeaderProcessing;
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
        final HeaderPredicate requestMerged = mergeHeader(Optional.ofNullable(httpLoggingConfiguration.getFilter()).map(LogFilter::getRequestHeaders).orElse(new HeaderPredicate(null, null)), predicateConfig.request().headers());
        final HeaderPredicate responseMerged = mergeHeader(Optional.ofNullable(httpLoggingConfiguration.getFilter()).map(LogFilter::getResponseHeaders).orElse(new HeaderPredicate(null, null)), predicateConfig.response().headers());
        return new PredicateConfig(predicateConfig.id(), predicateConfig.predicate(), new LogOptions(requestMerged, predicateConfig.request().raw(), predicateConfig.request().body()), new LogOptions(responseMerged, predicateConfig.response().raw(), predicateConfig.response().body()));
    }

    public static HeaderPredicate mergeHeader(HeaderPredicate global, HeaderPredicate local)
    {
        // Merge on the parsed header names, as the string form carries the processing instruction
        // as a suffix (for example 'Authorization,r') and would never match a plain header name.
        final Map<String, HeaderProcessing> includes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        includes.putAll(global.getIncludeProcessing());
        final Map<String, HeaderProcessing> excludes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        excludes.putAll(global.getExcludeProcessing());

        // Local includes overwrite global excludes
        local.getIncludeProcessing().keySet().forEach(excludes::remove);
        excludes.putAll(local.getExcludeProcessing());

        // Local excludes overwrite global includes
        local.getExcludeProcessing().keySet().forEach(includes::remove);
        includes.putAll(local.getIncludeProcessing());

        return HeaderPredicate.of(includes, excludes);
    }

    public PredicateConfig merge(PredicateConfig predicateConfig)
    {
        return cache.computeIfAbsent(predicateConfig.id(), (k) -> mergeFilter(httpLoggingConfiguration, predicateConfig));
    }

    public LogFilter getGlobalFilter()
    {
        return httpLoggingConfiguration.getFilter();
    }
}
