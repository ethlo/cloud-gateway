package com.ethlo.http.logger;

import org.jspecify.annotations.NonNull;

import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import reactor.core.publisher.Mono;

public interface HttpLogger
{
    Mono<@NonNull AccessLogResult> accessLog(WebExchangeDataProvider dataProvider);
}
