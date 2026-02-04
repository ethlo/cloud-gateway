package com.ethlo.http.logger.delegate;

import java.util.List;

import com.ethlo.chronograph.Chronograph;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;

public class SyncDelegateLogger extends BaseDelegateHttpLogger
{
    public SyncDelegateLogger(final List<HttpLogger> httpLoggers)
    {
        super(httpLoggers);
    }

    @Override
    public void accessLog(final Chronograph chronograph, final WebExchangeDataProvider dataProvider)
    {
        logWebExchangeData(chronograph, dataProvider);
    }
}