package com.ethlo.http.logger.delegate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.util.AsyncUtil;

/**
 * This logger will log to the delegate loggers in order.
 * An attempt is made to let subsequent loggers log even if one delegate logger fails.
 */
@Primary
@Component
@ConditionalOnProperty("http-logging.capture.enabled")
public class SequentialDelegateLogger
{
    private static final Logger logger = LoggerFactory.getLogger(SequentialDelegateLogger.class);
    private final List<HttpLogger> httpLoggers;

    public SequentialDelegateLogger(final List<HttpLogger> httpLoggers)
    {
        this.httpLoggers = httpLoggers;
        if (httpLoggers.isEmpty())
        {
            logger.warn("No access logger(s) configured!");
        }
        else
        {
            logger.info("Using {} loggers:", httpLoggers.size());
            httpLoggers.forEach(l -> logger.info(l.toString()));
        }
    }

    public CompletableFuture<AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        final PredicateConfig predicateConfig = dataProvider.getPredicateConfig().orElseThrow();
        return AsyncUtil.join(httpLoggers.stream()
                        .map(httpLogger -> httpLogger.accessLog(dataProvider))
                        .toList())
                .thenApply(list ->
                {
                    AccessLogResult result = AccessLogResult.ok(predicateConfig);
                    for (AccessLogResult l : list)
                    {
                        result = result.combine(l);
                    }
                    return result;
                });
    }
}
