package com.ethlo.http.logger.delegate;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;

@Primary
@Component
@ConditionalOnProperty("http-logging.capture.enabled")
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
        else
        {
            logger.info("Using {} loggers:", loggers.size());
            loggers.forEach(l -> logger.info(l.toString()));
        }
    }

    @Override
    public void accessLog(final WebExchangeDataProvider dataProvider)
    {
        loggers.forEach(l -> l.accessLog(dataProvider));
    }
}
