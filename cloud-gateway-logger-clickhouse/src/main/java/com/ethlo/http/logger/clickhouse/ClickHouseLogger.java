package com.ethlo.http.logger.clickhouse;

import static com.ethlo.http.match.HeaderProcessing.DELETE;
import static com.ethlo.http.match.HeaderProcessing.REDACT;
import static com.ethlo.http.match.LogOptions.ContentProcessing.STORE;
import static com.ethlo.http.netty.ServerDirection.REQUEST;
import static com.ethlo.http.netty.ServerDirection.RESPONSE;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.LoggingFilterService;
import com.ethlo.http.logger.RedactUtil;
import com.ethlo.http.match.HeaderProcessing;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public class ClickHouseLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseLogger.class);
    private final LoggingFilterService loggingFilterService;
    private final ClickHouseLoggerRepository clickHouseLoggerRepository;
    private final Scheduler ioScheduler;

    public ClickHouseLogger(LoggingFilterService loggingFilterService, ClickHouseLoggerRepository clickHouseLoggerRepository, Scheduler ioScheduler)
    {
        this.loggingFilterService = loggingFilterService;
        this.clickHouseLoggerRepository = clickHouseLoggerRepository;
        this.ioScheduler = ioScheduler;
    }

    private Mono<@NonNull Void> processContentReactive(LogOptions logConfig, BodyProvider bodyProvider, ServerDirection dir, Map<String, Object> params)
    {
        if (bodyProvider == null)
        {
            return Mono.empty();
        }

        return Mono.<Void>fromRunnable(() ->
        {
            try (final InputStream inputStream = bodyProvider.getInputStream())
            {
                final byte[] body = inputStream.readAllBytes();
                final String prefix = dir.name().toLowerCase();
                params.put(prefix + "_total_size", body.length);
                params.put(prefix + "_body_size", body.length);

                if (logConfig.body() == STORE || logConfig.raw() == STORE)
                {
                    params.put(prefix + "_body", body);
                }
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }).subscribeOn(ioScheduler);
    }

    private void initializeParams(Map<String, Object> params)
    {
        final List<String> prefixes = List.of("request", "response");
        for (String prefix : prefixes)
        {
            params.put(prefix + "_raw", null);
            params.put(prefix + "_body", null);
            params.put(prefix + "_body_size", null);
            params.put(prefix + "_total_size", null);
        }
        params.put("exception_type", null);
        params.put("exception_message", null);
    }

    @Override
    public Mono<@NonNull AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        final Optional<PredicateConfig> logConfigOpt = dataProvider.getPredicateConfig();
        if (logConfigOpt.isEmpty())
        {
            return Mono.empty();
        }

        final PredicateConfig predicateConfig = loggingFilterService.merge(logConfigOpt.get());
        final Map<String, Object> params = dataProvider.asMetaMap();

        initializeParams(params);

        // Metadata processing
        prepareHeaders(dataProvider, predicateConfig, params);
        dataProvider.getException().ifPresent(exc ->
        {
            params.put("exception_type", exc.getClass().getName());
            params.put("exception_message", exc.getMessage());
        });

        // The entire chain (loading files + DB insertion) is offloaded to the ioScheduler
        return Mono.when(
                        processContentReactive(predicateConfig.request(), dataProvider.getRequestBody().orElse(null), REQUEST, params),
                        processContentReactive(predicateConfig.response(), dataProvider.getResponseBody().orElse(null), RESPONSE, params)
                )
                .then(Mono.fromRunnable(() -> {
                    logger.debug("Inserting data into ClickHouse for request {}", dataProvider.getRequestId());
                    clickHouseLoggerRepository.insert(params);
                }))
                .thenReturn(AccessLogResult.ok(dataProvider))
                .subscribeOn(ioScheduler);
    }

    private void prepareHeaders(WebExchangeDataProvider dataProvider, PredicateConfig predicateConfig, Map<String, Object> params)
    {
        final HttpHeaders requestHeaders = HttpHeaders.copyOf(dataProvider.getRequestHeaders());
        final HttpHeaders responseHeaders = HttpHeaders.copyOf(dataProvider.getResponseHeaders());

        // Request Header Scrubbing
        processHeader(DELETE, requestHeaders, HttpHeaders.HOST);
        processHeader(REDACT, requestHeaders, HttpHeaders.AUTHORIZATION);
        requestHeaders.headerNames().forEach(name -> processHeader(predicateConfig.request().headers().apply(name), requestHeaders, name));

        // Response Header Scrubbing
        responseHeaders.headerNames().forEach(name -> processHeader(predicateConfig.response().headers().apply(name), responseHeaders, name));

        params.put("request_headers", flattenMap(requestHeaders));
        params.put("response_headers", flattenMap(responseHeaders));
    }

    private void processHeader(HeaderProcessing processing, HttpHeaders headers, String name)
    {
        if (processing == DELETE) headers.remove(name);
        else if (processing == REDACT)
        {
            List<String> values = headers.get(name);
            if (values != null) headers.put(name, RedactUtil.redactAll(values));
        }
    }

    private Map<String, String> flattenMap(HttpHeaders headers)
    {
        return headers.headerNames().stream()
                .collect(Collectors.toMap(k -> k, k -> String.join(", ", Objects.requireNonNull(headers.get(k)))));
    }
}