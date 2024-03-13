package com.ethlo.http.logger;

import com.ethlo.http.model.WebExchangeDataProvider;
import reactor.core.publisher.Mono;

public interface HttpLogger
{
    void accessLog(WebExchangeDataProvider dataProvider);
}
