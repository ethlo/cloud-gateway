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
import reactor.core.scheduler.Schedulers;

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
        // Emit to the sink for external subscribers (e.g., metrics or UI)
        return Mono.fromCallable(() ->
                {
                    AccessLogResult combinedResult = AccessLogResult.ok(dataProvider);

                    for (HttpLogger httpLogger : httpLoggers)
                    {
                        try
                        {
                            // Execution is now linear and blocking on the IO thread
                            final AccessLogResult result = httpLogger.accessLog(dataProvider);
                            combinedResult = combinedResult.combine(result);
                        }
                        catch (Exception e)
                        {
                            logger.error("Logger {} failed for request {}", httpLogger.getClass().getSimpleName(), dataProvider.getRequestId(), e);
                            combinedResult = combinedResult.combine(AccessLogResult.error(dataProvider, List.of(e)));
                        }
                    }
                    return combinedResult;
                })
                // Crucial: Offload the entire blocking loop to the IO scheduler
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(resultSink::tryEmitNext)
                .doOnError(e ->
                {
                    final Exception ex = e instanceof Exception ? (Exception) e : new RuntimeException(e);
                    resultSink.tryEmitNext(AccessLogResult.error(dataProvider, List.of(ex)));
                });
    }

    public Flux<@NonNull AccessLogResult> getResults()
    {
        return resultSink.asFlux();
    }
}