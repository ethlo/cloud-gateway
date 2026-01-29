package com.ethlo.http.logger.file;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public class FileLogger implements HttpLogger
{
    private static final Logger accessLogLogger = LoggerFactory.getLogger("access-log");

    private final AccessLogTemplateRenderer accessLogTemplateRenderer;
    private final Scheduler ioScheduler;

    public FileLogger(final AccessLogTemplateRenderer accessLogTemplateRenderer, final Scheduler ioScheduler)
    {
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
        this.ioScheduler = ioScheduler;
    }

    public Mono<@NonNull AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        return Mono.fromCallable(() ->
                {
                    final Map<String, Object> metaMap = dataProvider.asMetaMap();
                    accessLogLogger.info(accessLogTemplateRenderer.render(metaMap));
                    return AccessLogResult.ok(dataProvider.getPredicateConfig().orElseThrow(() -> new IllegalStateException("No predicate config found for request " + dataProvider.getRequestId())));
                })
                .subscribeOn(ioScheduler); // Ensure template rendering/logging offloads from EventLoop
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " - pattern='" + accessLogTemplateRenderer.getPattern() + "'";
    }
}