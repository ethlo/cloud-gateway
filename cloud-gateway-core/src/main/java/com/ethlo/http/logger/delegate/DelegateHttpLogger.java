package com.ethlo.http.logger.delegate;

import java.util.function.Consumer;

import com.ethlo.chronograph.Chronograph;
import com.ethlo.http.model.WebExchangeDataProvider;

public interface DelegateHttpLogger
{
    void accessLog(Chronograph chronograph, WebExchangeDataProvider dataProvider);

    void addListener(Consumer<WebExchangeDataProvider> listener);

    void removeListener(Consumer<WebExchangeDataProvider> listener);
}
