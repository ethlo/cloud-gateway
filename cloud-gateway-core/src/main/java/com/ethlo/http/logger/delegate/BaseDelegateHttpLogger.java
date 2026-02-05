package com.ethlo.http.logger.delegate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.chronograph.Chronograph;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;

public abstract class BaseDelegateHttpLogger implements DelegateHttpLogger
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<HttpLogger> httpLoggers;

    private final List<Consumer<WebExchangeDataProvider>> listeners = new CopyOnWriteArrayList<>();

    public BaseDelegateHttpLogger(final List<HttpLogger> httpLoggers)
    {
        this.httpLoggers = httpLoggers;
        logger.info("Active loggers: {}", httpLoggers.stream().map(HttpLogger::getName).toList());
    }

    private void notifyListeners(WebExchangeDataProvider result)
    {
        for (Consumer<WebExchangeDataProvider> listener : listeners)
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

    @Override
    public void addListener(Consumer<WebExchangeDataProvider> listener)
    {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(Consumer<WebExchangeDataProvider> listener)
    {
        this.listeners.remove(listener);
    }

    protected void logWebExchangeData(Chronograph chronograph, WebExchangeDataProvider dataProvider)
    {
        try
        {
            for (HttpLogger httpLogger : httpLoggers)
            {
                logger.debug("Delegating to log {}", httpLogger.getName());
                try
                {
                    chronograph.time("logger_" + httpLogger.getName(), () -> httpLogger.accessLog(dataProvider));
                }
                catch (Exception e)
                {
                    logger.error("Logger {} failed for request {}",
                            httpLogger.getClass().getSimpleName(), dataProvider.getRequestId(), e
                    );
                }
            }
        } finally
        {
            notifyListeners(dataProvider);
        }
    }

    @Override
    public void close() throws Exception
    {
        for (HttpLogger httpLogger : httpLoggers)
        {
            try
            {
                httpLogger.close();
            }
            catch (Exception e)
            {
                logger.warn("Logger {} failed to close", httpLogger.getName(), e);
            }
        }
    }
}
