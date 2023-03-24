package com.ethlo.http.logger.delegate;

import java.io.BufferedInputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.ethlo.http.logger.HttpLogger;

@Primary
@Component
public class DelegateLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(DelegateLogger.class);
    private final List<HttpLogger> loggers;

    public DelegateLogger(final List<HttpLogger> loggers)
    {
        this.loggers = loggers;
        if (loggers.isEmpty())
        {
            logger.warn("No access logger(s) configured!");
        }
    }

    @Override
    public void accessLog(final Map<String, Object> data, final BufferedInputStream requestData, final BufferedInputStream responseData)
    {
        for (HttpLogger logger : loggers)
        {
            logger.accessLog(data, requestData, responseData);
        }
    }
}
