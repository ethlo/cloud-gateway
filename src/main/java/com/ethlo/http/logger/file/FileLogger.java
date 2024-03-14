package com.ethlo.http.logger.file;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;

public class FileLogger implements HttpLogger
{
    private static final Logger accessLogLogger = LoggerFactory.getLogger("access-log");

    private final AccessLogTemplateRenderer accessLogTemplateRenderer;

    public FileLogger(final AccessLogTemplateRenderer accessLogTemplateRenderer)
    {
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
    }

    @Override
    public CompletableFuture<AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        final Map<String, Object> metaMap = dataProvider.asMetaMap();
        accessLogLogger.info(accessLogTemplateRenderer.render(metaMap));
        final CompletableFuture<AccessLogResult> cf = new CompletableFuture<>();
        cf.complete(AccessLogResult.ok(dataProvider.getPredicateConfig().orElseThrow()));
        return cf;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " - pattern='" + accessLogTemplateRenderer.getPattern() + "'";
    }
}
