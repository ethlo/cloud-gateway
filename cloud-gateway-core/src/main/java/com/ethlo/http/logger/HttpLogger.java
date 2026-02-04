package com.ethlo.http.logger;

import com.ethlo.http.model.WebExchangeDataProvider;

public interface HttpLogger extends AutoCloseable
{
    void accessLog(WebExchangeDataProvider dataProvider);

    String getName();
}
