package com.ethlo.http.logger.file;

import java.io.BufferedInputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;

public class FileLogger implements HttpLogger
{
    private static final Logger accessLogLogger = LoggerFactory.getLogger("access-log");
    private final AccessLogTemplateRenderer accessLogTemplateRenderer;

    public FileLogger(final AccessLogTemplateRenderer accessLogTemplateRenderer)
    {
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
    }

    @Override
    public void accessLog(final Map<String, Object> data, final BufferedInputStream requestData, final BufferedInputStream responseData)
    {
        if (accessLogLogger.isInfoEnabled())
        {
            accessLogLogger.info(accessLogTemplateRenderer.render(data));
        }
    }
}
