package com.ethlo.http.logger.delegate;

import java.util.List;

import com.ethlo.http.model.AccessLogResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;

/**
 * This logger will log to the delegate loggers in order.
 * An attempt is made to let subsequent loggers log even if one delegate logger fails.
 */
@Primary
@Component
@ConditionalOnProperty("http-logging.capture.enabled")
public class SequentialDelegateLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(SequentialDelegateLogger.class);
    private final List<HttpLogger> loggers;

    public SequentialDelegateLogger(final List<HttpLogger> loggers)
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
    public AccessLogResult accessLog(final WebExchangeDataProvider dataProvider)
    {
        AccessLogResult result = AccessLogResult.ok(dataProvider.getPredicateConfig().orElseThrow());
        for (final HttpLogger logger : loggers)
        {
            result = result.combine(logger.accessLog(dataProvider));
        }
        return result;
    }
}
