package com.ethlo.http.logger;

import java.util.concurrent.CompletableFuture;

import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;

public interface HttpLogger
{
    CompletableFuture<AccessLogResult> accessLog(WebExchangeDataProvider dataProvider);
}
