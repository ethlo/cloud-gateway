package com.ethlo.http.logger;

import com.ethlo.http.model.WebExchangeDataProvider;

public interface HttpLogger
{
    void accessLog(WebExchangeDataProvider dataProvider);
}
