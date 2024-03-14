package com.ethlo.http.model;

import java.util.ArrayList;
import java.util.List;

import com.ethlo.http.netty.PredicateConfig;

public class AccessLogResult
{
    private final PredicateConfig predicateConfig;
    private final List<? extends Exception> processingErrors;

    public static AccessLogResult ok(PredicateConfig predicateConfig)
    {
        return new AccessLogResult(predicateConfig, List.of());
    }

    private AccessLogResult(final PredicateConfig predicateConfig, final List<? extends Exception> processingErrors)
    {
        this.predicateConfig = predicateConfig;
        this.processingErrors = processingErrors;
    }

    public static AccessLogResult error(PredicateConfig predicateConfig, List<? extends Exception> processingErrors)
    {
        return new AccessLogResult(predicateConfig, processingErrors);
    }

    public PredicateConfig getPredicateConfig()
    {
        return predicateConfig;
    }


    public List<? extends Exception> getProcessingErrors()
    {
        return processingErrors;
    }

    public AccessLogResult combine(AccessLogResult other)
    {
        final List<Exception> combined = new ArrayList<>(this.processingErrors);
        combined.addAll(other.processingErrors);
        return new AccessLogResult(predicateConfig, combined);
    }

    public boolean isOk()
    {
        return this.processingErrors.isEmpty();
    }
}
