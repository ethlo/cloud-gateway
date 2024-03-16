package com.ethlo.http.logger.clickhouse;

import static com.ethlo.http.match.LogOptions.ContentProcessing.SIZE;
import static com.ethlo.http.match.LogOptions.ContentProcessing.STORE;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ethlo.http.BodyDecodeException;
import com.ethlo.http.logger.HttpLogger;
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
import com.google.common.base.Stopwatch;

public class ClickHouseLogger implements HttpLogger
{
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseLogger.class);
    private final NamedParameterJdbcTemplate tpl;

    public ClickHouseLogger(final NamedParameterJdbcTemplate tpl)
    {
        this.tpl = tpl;
    }

    private static CompletableFuture<BodyDecodeException> processContent(LogOptions logConfig, RawProvider rawProvider, final ServerDirection serverDirection, final Map<String, Object> params)
    {
        final String keyPrefix = serverDirection.name().toLowerCase();
        if ((logConfig.raw() == STORE || logConfig.body() == STORE || logConfig.body() == SIZE) && rawProvider != null)
        {
            return rawProvider.getBuffer().thenApply(buffer ->
            {
                final byte[] responseData = buffer.array();
                params.put(keyPrefix + "_total_size", rawProvider.size());
                if (logConfig.raw() == STORE)
                {
                    params.put(keyPrefix + "_raw", responseData);
                }

                if (logConfig.body() == STORE || logConfig.body() == SIZE)
                {
                    try
                    {
                        final BodyProvider bodyProvider = HttpBodyUtil.extractBody(new ByteArrayInputStream(responseData), serverDirection);
                        params.put(keyPrefix + "_body_size", bodyProvider.bodyLength());
                        if (logConfig.body() == STORE)
                        {
                            params.put(keyPrefix + "_body", IoUtil.readAllBytes(bodyProvider.data()));
                        }
                    }
                    catch (BodyDecodeException exc)
                    {
                        return exc;
                    }
                }
                return null;
            });
        }
        final CompletableFuture<BodyDecodeException> result = new CompletableFuture<>();
        result.complete(null);
        return result;
    }

    @Override
    public CompletableFuture<AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        final Optional<PredicateConfig> logConfigOpt = dataProvider.getPredicateConfig();
        if (logConfigOpt.isEmpty())
        {
            return null;
        }

        final PredicateConfig predicateConfig = logConfigOpt.get();

        final Map<String, Object> params = dataProvider.asMetaMap();

        dataProvider.requestHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getRequestHeaders()));
        dataProvider.responseHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getResponseHeaders()));

        // Remove headers already captured in dedicated columns
        dataProvider.getRequestHeaders().remove(HttpHeaders.HOST);
        dataProvider.getRequestHeaders().remove(HttpHeaders.AUTHORIZATION);
        dataProvider.getRequestHeaders().remove(HttpHeaders.USER_AGENT);
        dataProvider.getRequestHeaders().remove(HttpHeaders.CONTENT_TYPE);

        params.put("request_raw", null);
        params.put("request_body", null);
        params.put("request_body_size", null);
        params.put("request_total_size", null);

        params.put("response_raw", null);
        params.put("response_body", null);
        params.put("response_body_size", null);
        params.put("response_total_size", null);

        params.put("request_headers", flattenMap(dataProvider.getRequestHeaders()));
        params.put("response_headers", flattenMap(dataProvider.getResponseHeaders()));

        return AsyncUtil.join(List.of(
                        processContent(predicateConfig.request(), dataProvider.getRawRequest().orElse(null), ServerDirection.REQUEST, params),
                        processContent(predicateConfig.request(), dataProvider.getRawResponse().orElse(null), ServerDirection.RESPONSE, params)
                )
        ).thenApply(res ->
        {
            final List<BodyDecodeException> processingResult = res.stream().filter(Objects::nonNull).toList();
            logger.debug("Inserting data into ClickHouse for request {}", dataProvider.getRequestId());
            final Stopwatch stopwatch = Stopwatch.createStarted();
            try
            {
                tpl.update("""
                                                    
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
                                  :request_body, :response_body, :request_raw, :response_raw)""",
                        params
                );
                logger.debug("Finished inserting data into ClickHouse for request {} in {}", dataProvider.getRequestId(), stopwatch.elapsed());
            }
            catch (RuntimeException exc)
            {
                logger.error("An error occurred inserting into clickhouse", exc);
                throw exc;
            }

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
