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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.RedactUtil;
import com.ethlo.http.match.HeaderProcessing;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;

public class ClickHouseLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseLogger.class);
    private final ClickHouseLoggerRepository clickHouseLoggerRepository;

    public ClickHouseLogger(ClickHouseLoggerRepository clickHouseLoggerRepository)
    {
        this.clickHouseLoggerRepository = clickHouseLoggerRepository;
    }

    @Override
    public void accessLog(final WebExchangeDataProvider dataProvider)
    {
        final PredicateConfig predicateConfig = dataProvider.getPredicateConfig();
        final Map<String, Object> params = dataProvider.asMetaMap();

        initializeParams(params);

        prepareHeaders(dataProvider, predicateConfig, params);
        dataProvider.getException().ifPresent(exc -> {
            params.put("exception_type", exc.getClass().getName());
            params.put("exception_message", exc.getMessage());
        });

        dataProvider.getRequestBody().ifPresent(bodyProvider -> processContentSync(predicateConfig.request(), bodyProvider, REQUEST, params));
        dataProvider.getResponseBody().ifPresent(bodyProvider -> processContentSync(predicateConfig.response(), bodyProvider, RESPONSE, params));

        try
        {
            logger.debug("Inserting data into ClickHouse for request {}", dataProvider.getRequestId());
            clickHouseLoggerRepository.insert(params);
        }
        catch (Exception e)
        {
            logger.error("Failed to insert log into Clickhouse for request {}", dataProvider.getRequestId(), e);
        }
    }

    @Override
    public String getName()
    {
        return "clickhouse";
    }

    private void processContentSync(LogOptions logConfig, BodyProvider bodyProvider, ServerDirection dir, Map<String, Object> params)
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
            logger.error("Failed to read captured body file for {} direction: {}", dir, bodyProvider.file(), e);
            throw new UncheckedIOException(e);
        }
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
        if (processing == DELETE)
        {
            headers.remove(name);
        }
        else if (processing == REDACT)
        {
            final List<String> values = headers.get(name);
            if (values != null)
            {
                headers.put(name, RedactUtil.redactAll(values));
            }
        }
    }

    private Map<String, String> flattenMap(HttpHeaders headers)
    {
        return headers.headerNames().stream()
                .collect(Collectors.toMap(k -> k, k -> String.join(", ", Objects.requireNonNull(headers.get(k)))));
    }

    @Override
    public void close()
    {
        // Nothing
    }
}