package com.ethlo.http.logger.clickhouse;

import static com.ethlo.http.match.HeaderProcessing.DELETE;
import static com.ethlo.http.match.HeaderProcessing.REDACT;
import static com.ethlo.http.match.LogOptions.ContentProcessing.STORE;
import static com.ethlo.http.netty.ServerDirection.REQUEST;
import static com.ethlo.http.netty.ServerDirection.RESPONSE;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.BodyDecodeException;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.LoggingFilterService;
import com.ethlo.http.logger.RedactUtil;
import com.ethlo.http.match.HeaderProcessing;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.RawProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import com.ethlo.http.util.AsyncUtil;
import com.ethlo.http.util.HttpBodyUtil;
import com.ethlo.http.util.IoUtil;

public class ClickHouseLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseLogger.class);
    private final LoggingFilterService loggingFilterService;
    private final ClickHouseLoggerRepository clickHouseLoggerRepository;

    public ClickHouseLogger(final LoggingFilterService loggingFilterService, ClickHouseLoggerRepository clickHouseLoggerRepository)
    {
        this.loggingFilterService = loggingFilterService;
        this.clickHouseLoggerRepository = clickHouseLoggerRepository;
    }

    private static CompletableFuture<BodyDecodeException> processContent(LogOptions logConfig, RawProvider rawProvider, final ServerDirection serverDirection, final Map<String, Object> params)
    {
        final String keyPrefix = serverDirection.name().toLowerCase();
        if (rawProvider != null)
        {
            logger.debug("Setting total size: {}", serverDirection);
            params.put(keyPrefix + "_total_size", rawProvider.size());
            return rawProvider.getBuffer()
                    .map(future -> future.thenApply(b ->
                    {
                        final byte[] responseData = b.array();
                        if (logConfig.raw() == STORE)
                        {
                            logger.debug("Setting raw: {}", serverDirection);
                            params.put(keyPrefix + "_raw", responseData);
                        }
                        return processBody(params, logConfig, keyPrefix, responseData, serverDirection).orElse(null);
                    })).orElse(CompletableFuture.completedFuture(null));
        }
        return CompletableFuture.completedFuture(null);
    }

    private static Optional<BodyDecodeException> processBody(final Map<String, Object> params, final LogOptions logConfig, final String keyPrefix, final byte[] responseData, final ServerDirection serverDirection)
    {
        if (logConfig.body() != null)
        {
            final BodyProvider bodyProvider;
            try
            {
                bodyProvider = HttpBodyUtil.extractBody(new ByteArrayInputStream(responseData), serverDirection);
            }
            catch (BodyDecodeException e)
            {
                return Optional.of(e);
            }
            params.put(keyPrefix + "_body_size", bodyProvider.bodyLength());
            if (logConfig.body() == STORE)
            {
                params.put(keyPrefix + "_body", IoUtil.readAllBytes(bodyProvider.data()));
            }
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        final Optional<PredicateConfig> logConfigOpt = dataProvider.getPredicateConfig();
        if (logConfigOpt.isEmpty())
        {
            return null;
        }

        final PredicateConfig predicateConfig = loggingFilterService.merge(logConfigOpt.get());


        final Map<String, Object> params = dataProvider.asMetaMap();

        dataProvider.requestHeaders(HttpHeaders.copyOf(dataProvider.getRequestHeaders()));
        dataProvider.responseHeaders(HttpHeaders.copyOf(dataProvider.getResponseHeaders()));

        // Remove headers already captured in dedicated columns or otherwise processed
        final HttpHeaders requestHeaders = dataProvider.getRequestHeaders();
        processHeader(DELETE, REQUEST, requestHeaders, HttpHeaders.HOST);
        processHeader(REDACT, REQUEST, requestHeaders, HttpHeaders.AUTHORIZATION);
        processHeader(DELETE, REQUEST, requestHeaders, HttpHeaders.USER_AGENT);
        processHeader(DELETE, REQUEST, requestHeaders, HttpHeaders.CONTENT_TYPE);

        // Remove headers that are not requested for logging
        for (final String headerName : requestHeaders.headerNames())
        {
            final HeaderProcessing processing = predicateConfig.request().headers().apply(headerName);
            processHeader(processing, REQUEST, requestHeaders, headerName);
        }

        params.put("exception_type", null);
        params.put("exception_message", null);
        dataProvider.getException().ifPresent(exc ->
        {
            params.put("exception_type", exc.getClass().getName());
            params.put("exception_message", exc.getMessage());
        });

        params.put("request_raw", null);
        params.put("request_body", null);
        params.put("request_body_size", null);
        params.put("request_total_size", null);

        params.put("response_raw", null);
        params.put("response_body", null);
        params.put("response_body_size", null);
        params.put("response_total_size", null);

        final HttpHeaders responseHeaders = dataProvider.getResponseHeaders();

        // Remove headers that are not requested for logging
        for (final String headerName : responseHeaders.headerNames())
        {
            final HeaderProcessing processing = predicateConfig.response().headers().apply(headerName);
            processHeader(processing, RESPONSE, responseHeaders, headerName);
        }

        params.put("request_headers", flattenMap(requestHeaders));
        params.put("response_headers", flattenMap(responseHeaders));

        return AsyncUtil.join(List.of(
                        processContent(predicateConfig.request(), dataProvider.getRawRequest().orElse(null), REQUEST, params),
                        processContent(predicateConfig.request(), dataProvider.getRawResponse().orElse(null), RESPONSE, params)
                )
        ).thenApply(res ->
        {
            final List<BodyDecodeException> processingResult = res.stream().filter(Objects::nonNull).toList();
            logger.debug("Inserting data into ClickHouse for request {}: {}", dataProvider.getRequestId(), params);
            insertIntoDatabase(params);

            if (processingResult.isEmpty())
            {
                return AccessLogResult.ok(predicateConfig);
            }
            else
            {
                return AccessLogResult.error(predicateConfig, processingResult);
            }
        });
    }

    protected void insertIntoDatabase(Map<String, Object> params)
    {
        clickHouseLoggerRepository.insert(params);
    }

    private void processHeader(final HeaderProcessing headerProcessing, final ServerDirection direction, HttpHeaders headers, String headerName)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Processing {} header {} with instruction {}", direction.name().toLowerCase(), headerName, headerProcessing);
        }

        if (headerProcessing == DELETE)
        {
            headers.remove(headerName);
        }
        else if (headerProcessing == REDACT)
        {
            final List<String> values = headers.get(headerName);
            if (values != null)
            {
                final List<String> redacted = RedactUtil.redactAll(values);
                headers.remove(headerName);
                headers.addAll(headerName, redacted);
            }
        }
    }

    private Map<String, String> flattenMap(HttpHeaders headers)
    {
        return headers.headerNames().stream()
                .collect(Collectors.toMap(
                        key -> key,
                        key -> String.join(", ", Objects.requireNonNull(headers.get(key)))
                ));
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " Filter={" + loggingFilterService.getGlobalFilter() + "}";
    }
}
