package com.ethlo.http.logger.delegate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;

public class SequentialDelegateLogger
{
    private static final Logger logger = LoggerFactory.getLogger(SequentialDelegateLogger.class);

    private final List<HttpLogger> httpLoggers;

    private final List<Consumer<AccessLogResult>> listeners = new CopyOnWriteArrayList<>();

    public SequentialDelegateLogger(final List<HttpLogger> httpLoggers)
    {
        this.httpLoggers = httpLoggers;
    }

    public AccessLogResult accessLog(final WebExchangeDataProvider dataProvider)
    {
        AccessLogResult combinedResult = AccessLogResult.ok(dataProvider);

        for (HttpLogger httpLogger : httpLoggers)
        {
            try
            {
                // Simple linear blocking execution
                final AccessLogResult result = httpLogger.accessLog(dataProvider);
                combinedResult = combinedResult.combine(result);
            }
            catch (Exception e)
            {
                logger.error("Logger {} failed for request {}",
                        httpLogger.getClass().getSimpleName(), dataProvider.getRequestId(), e
                );

                combinedResult = combinedResult.combine(
                        AccessLogResult.error(dataProvider, List.of(e))
                );
            }
        }

        notifyListeners(combinedResult);

        return combinedResult;
    }

    private void notifyListeners(AccessLogResult result)
    {
        for (Consumer<AccessLogResult> listener : listeners)
        {
            try
            {
                listener.accept(result);
            }
            catch (Exception e)
            {
                logger.error("Listener failed to process access log result", e);
            }
        }
    }

    public void addListener(Consumer<AccessLogResult> listener)
    {
        this.listeners.add(listener);
    }

    public void removeListener(Consumer<AccessLogResult> listener)
    {
        this.listeners.remove(listener);
    }
}