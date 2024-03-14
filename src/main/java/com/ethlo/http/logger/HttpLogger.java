package com.ethlo.http.logger;

import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import reactor.core.publisher.Mono;

public interface HttpLogger
{
    AccessLogResult accessLog(WebExchangeDataProvider dataProvider);
}
