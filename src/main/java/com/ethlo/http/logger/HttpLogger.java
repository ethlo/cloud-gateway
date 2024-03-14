package com.ethlo.http.logger;

import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;

public interface HttpLogger
{
    AccessLogResult accessLog(WebExchangeDataProvider dataProvider);
}
