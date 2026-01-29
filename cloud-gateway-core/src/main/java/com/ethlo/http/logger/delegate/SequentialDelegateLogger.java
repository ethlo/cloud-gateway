package com.ethlo.http.logger.delegate;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class SequentialDelegateLogger
{
    private static final Logger logger = LoggerFactory.getLogger(SequentialDelegateLogger.class);
    private final List<HttpLogger> httpLoggers;

    private final Sinks.Many<AccessLogResult> resultSink = Sinks.many().multicast().directBestEffort();

    public SequentialDelegateLogger(final List<HttpLogger> httpLoggers)
    {
        this.httpLoggers = httpLoggers;
    }

    public Mono<AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        final PredicateConfig predicateConfig = dataProvider.getPredicateConfig()
                .orElseThrow(() -> new IllegalStateException("No predicate config for request " + dataProvider.getRequestId()));

        return Flux.fromIterable(httpLoggers)
                .flatMap(httpLogger -> httpLogger.accessLog(dataProvider)
                        .onErrorResume(e -> {
                            logger.error("Logger {} failed for request {}", httpLogger, dataProvider.getRequestId(), e);
                            // Explicitly cast or wrap to match your error signature
                            final Exception ex = e instanceof Exception ? (Exception) e : new RuntimeException(e);
                            return Mono.just(AccessLogResult.error(predicateConfig, List.of(ex)));
                        }))
                .reduce(AccessLogResult.ok(predicateConfig), AccessLogResult::combine)
                .doOnError(e -> {
                    resultSink.tryEmitNext(AccessLogResult.error(predicateConfig, List.of(new Exception(e))));
                })
                .doOnNext(resultSink::tryEmitNext); // Broadcast the result
    }

    public Flux<AccessLogResult> getResults()
    {
        return resultSink.asFlux();
    }
}