package com.ethlo.http.logger;

import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

public interface HttpLogger
{
    CompletableFuture<AccessLogResult> accessLog(WebExchangeDataProvider dataProvider);
}
