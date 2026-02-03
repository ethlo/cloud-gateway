package com.ethlo.http.model;

import java.util.ArrayList;
import java.util.List;

public class AccessLogResult
{
    private final WebExchangeDataProvider webExchangeDataProvider;
    private final List<? extends Exception> processingErrors;

    private AccessLogResult(final WebExchangeDataProvider webExchangeDataProvider, final List<? extends Exception> processingErrors)
    {
        this.webExchangeDataProvider = webExchangeDataProvider;
        this.processingErrors = processingErrors;
    }

    public static AccessLogResult ok(final WebExchangeDataProvider webExchangeDataProvider)
    {
        return new AccessLogResult(webExchangeDataProvider, List.of());
    }

    public static AccessLogResult error(final WebExchangeDataProvider webExchangeDataProvider, List<? extends Exception> processingErrors)
    {
        return new AccessLogResult(webExchangeDataProvider, processingErrors);
    }

    public void cleanup()
    {
        webExchangeDataProvider.cleanup();
    }

    public List<? extends Exception> getProcessingErrors()
    {
        return processingErrors;
    }

    public AccessLogResult combine(AccessLogResult other)
    {
        final List<Exception> combined = new ArrayList<>(this.processingErrors);
        combined.addAll(other.processingErrors);
        return new AccessLogResult(webExchangeDataProvider, combined);
    }

    public WebExchangeDataProvider getWebExchangeDataProvider()
    {
        return webExchangeDataProvider;
    }

    public boolean isOk()
    {
        return this.processingErrors.isEmpty();
    }
}
