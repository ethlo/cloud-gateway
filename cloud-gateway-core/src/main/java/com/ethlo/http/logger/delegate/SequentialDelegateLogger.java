package com.ethlo.http.logger.delegate;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class SequentialDelegateLogger
{
    private static final Logger logger = LoggerFactory.getLogger(SequentialDelegateLogger.class);
    private final List<HttpLogger> httpLoggers;

    private final Sinks.Many<@NonNull AccessLogResult> resultSink = Sinks.many().multicast().directBestEffort();

    public SequentialDelegateLogger(final List<HttpLogger> httpLoggers)
    {
        this.httpLoggers = httpLoggers;
    }

    public Mono<@NonNull AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        return Flux.fromIterable(httpLoggers)
                .flatMap(httpLogger -> httpLogger.accessLog(dataProvider)
                        .onErrorResume(e -> {
                            logger.error("Logger {} failed for request {}", httpLogger, dataProvider.getRequestId(), e);
                            final Exception ex = e instanceof Exception ? (Exception) e : new RuntimeException(e);
                            return Mono.just(AccessLogResult.error(dataProvider, List.of(ex)));
                        }))
                .reduce(AccessLogResult.ok(dataProvider), AccessLogResult::combine)
                .doOnError(e -> {
                    resultSink.tryEmitNext(AccessLogResult.error(dataProvider, List.of(new Exception(e))));
                })
                .doOnNext(resultSink::tryEmitNext);
    }

    public Flux<@NonNull AccessLogResult> getResults()
    {
        return resultSink.asFlux();
    }
}