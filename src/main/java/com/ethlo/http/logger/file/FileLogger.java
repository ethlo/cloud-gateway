package com.ethlo.http.logger.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
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
    public void accessLog(final WebExchangeDataProvider dataProvider)
    {
        if (accessLogLogger.isInfoEnabled())
        {
            accessLogLogger.info(accessLogTemplateRenderer.render(dataProvider.asMetaMap()));
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " - pattern='" + accessLogTemplateRenderer.getPattern() + "'";
    }
}
