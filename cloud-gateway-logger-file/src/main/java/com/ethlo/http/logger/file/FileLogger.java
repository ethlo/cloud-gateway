package com.ethlo.http.logger.file;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.blocking.model.WebExchangeDataProvider;

public class FileLogger implements HttpLogger
{
    private static final Logger accessLogLogger = LoggerFactory.getLogger("access-log");

    private final AccessLogTemplateRenderer accessLogTemplateRenderer;


    public FileLogger(final AccessLogTemplateRenderer accessLogTemplateRenderer)
    {
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
    }

    public AccessLogResult accessLog(final WebExchangeDataProvider dataProvider)
    {
        final Map<String, Object> metaMap = dataProvider.asMetaMap();
        accessLogLogger.info(accessLogTemplateRenderer.render(metaMap));
        return AccessLogResult.ok(dataProvider);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " - pattern='" + accessLogTemplateRenderer.getPattern() + "'";
    }
}