package com.ethlo.http.logger.clickhouse;

import static com.ethlo.http.match.LogOptions.ContentProcessing.SIZE;
import static com.ethlo.http.match.LogOptions.ContentProcessing.STORE;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.BodyDecodeException;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.RawProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import com.ethlo.http.util.HttpBodyUtil;
import com.ethlo.http.util.IoUtil;
import com.google.common.base.Stopwatch;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Mono;

public class ClickHouseLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseLogger.class);

    private static final String insertSql = """
            INSERT INTO log (
                timestamp, route_id, route_uri, gateway_request_id, method, path,
                response_time, request_body_size, response_body_size, request_total_size,
                response_total_size, status, is_error, user_claim, realm_claim, host,
                request_content_type, response_content_type, user_agent,
                request_headers, response_headers, request_body, response_body, request_raw, response_raw)
            VALUES(
                :timestamp, :route_id, :route_uri, :gateway_request_id, :method, :path,
                :duration, :request_body_size, :response_body_size,
                :request_total_size, :response_total_size, :status, :is_error, :user_claim, :realm_claim,
                :host, :request_content_type, :response_content_type, :user_agent,
                :request_headers, :response_headers,
                :request_body, :response_body, :request_raw, :response_raw)""";

    @Autowired
    private final ConnectionFactory connectionFactory;

    private static CompletableFuture<BodyDecodeException> processContent(LogOptions logConfig, RawProvider rawProvider, final ServerDirection serverDirection, Statement statement)
    {
        final String keyPrefix = serverDirection.name().toLowerCase();
        final AtomicReference<BodyDecodeException> bodyDecodeException = new AtomicReference<>();
        if ((logConfig.raw() == STORE || logConfig.body() == STORE || logConfig.body() == SIZE) && rawProvider != null)
        {
            return rawProvider.getBuffer().thenApply(buffer ->
            {
                final byte[] responseData = buffer.array();
                statement.bind(keyPrefix + "_total_size", rawProvider.size());
                if (logConfig.raw() == STORE)
                {
                    statement.bind(keyPrefix + "_raw", responseData);
                }

                if (logConfig.body() == STORE || logConfig.body() == SIZE)
                {
                    try
                    {
                        final BodyProvider bodyProvider = HttpBodyUtil.extractBody(new ByteArrayInputStream(responseData), serverDirection);
                        statement.bind(keyPrefix + "_body_size", bodyProvider.bodyLength());
                        if (logConfig.body() == STORE)
                        {
                            statement.bind(keyPrefix + "_body", IoUtil.readAllBytes(bodyProvider.data()));
                        }
                    }
                    catch (BodyDecodeException exc)
                    {
                        bodyDecodeException.set(exc);
                    }
                }
                return bodyDecodeException.get();
            });
        }
        final CompletableFuture<BodyDecodeException> empty = new CompletableFuture<>();
        empty.complete(null);
        return empty;
    }

    @Override
    public CompletableFuture<AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        return Mono.from(connectionFactory.create())
                .flatMap(conn ->
                {
                    final Statement statement = conn.createStatement(insertSql);
                    return populateStatement(statement, dataProvider);
                }).toFuture();
    }

    private Mono<AccessLogResult> populateStatement(final Statement statement, WebExchangeDataProvider dataProvider)
    {
        final Optional<PredicateConfig> logConfigOpt = dataProvider.getPredicateConfig();
        if (logConfigOpt.isEmpty())
        {
            return Mono.empty();
        }
        final PredicateConfig logConfig = logConfigOpt.get();

        dataProvider.requestHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getRequestHeaders()));
        dataProvider.responseHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getResponseHeaders()));

        // Remove headers already captured in dedicated columns
        dataProvider.getRequestHeaders().remove(HttpHeaders.HOST);
        dataProvider.getRequestHeaders().remove(HttpHeaders.AUTHORIZATION);
        dataProvider.getRequestHeaders().remove(HttpHeaders.USER_AGENT);
        dataProvider.getRequestHeaders().remove(HttpHeaders.CONTENT_TYPE);

        statement.bindNull("request_raw", byte[].class);
        statement.bindNull("request_body", byte[].class);
        statement.bindNull("request_body_size", Long.class);
        statement.bindNull("request_total_size", Long.class);

        statement.bindNull("response_raw", byte[].class);
        statement.bindNull("response_body", byte[].class);
        statement.bindNull("response_body_size", Long.class);
        statement.bindNull("response_total_size", Long.class);

        statement.bind("request_headers", flattenMap(dataProvider.getRequestHeaders()));
        statement.bind("response_headers", flattenMap(dataProvider.getResponseHeaders()));

        final AtomicReference<BodyDecodeException> bodyDecodeRequestExc = new AtomicReference<>();
        final AtomicReference<BodyDecodeException> bodyDecodeResponseExc = new AtomicReference<>();

        return processContent(logConfig.request(), dataProvider.getRawRequest().orElse(null), ServerDirection.REQUEST, statement)
                .thenCompose(requestResult ->
                        processContent(logConfig.response(), dataProvider.getRawResponse().orElse(null), ServerDirection.RESPONSE, statement)
                                .thenApply(responseResult ->
                                {
                                    bodyDecodeRequestExc.set(requestResult);
                                    logger.debug("Query params map processed");
                                    return null;
                                }).thenApply(ignored ->
                                {
                                    logger.debug("Inserting data into ClickHouse for request {}", dataProvider.getRequestId());
                                    final Stopwatch stopwatch = Stopwatch.createStarted();
                                    return Mono.from(statement.execute()).matoFuture();

                                    });
                                    logger.debug("Finished inserting data into ClickHouse for request {} in {}", dataProvider.getRequestId(), stopwatch.elapsed());

                                    if (bodyDecodeRequestExc.get() != null || bodyDecodeResponseExc.get() != null)
                                    {
                                        return AccessLogResult.error(logConfig, Stream.of(bodyDecodeRequestExc.get(), bodyDecodeResponseExc.get()).filter(Objects::nonNull).toList());
                                    }

                                    return AccessLogResult.ok(logConfig);
                                }));
    }

    private Map<String, Object> flattenMap(HttpHeaders headers)
    {
        final Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach((name, list) -> result.put(name, list.size() > 1 ? list : list.get(0)));
        return result;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }
}
