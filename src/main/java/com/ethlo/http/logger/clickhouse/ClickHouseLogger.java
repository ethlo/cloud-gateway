package com.ethlo.http.logger.clickhouse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.util.IoUtil;

public class ClickHouseLogger implements HttpLogger
{
    private final NamedParameterJdbcTemplate tpl;

    public ClickHouseLogger(final NamedParameterJdbcTemplate tpl)
    {
        this.tpl = tpl;
    }

    @Override
    public void accessLog(final WebExchangeDataProvider dataProvider)
    {
        final Map<String, Object> params = dataProvider.asMetaMap();

        // Remove headers already captured in dedicated columns
        dataProvider.requestHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getRequestHeaders()));
        dataProvider.responseHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getResponseHeaders()));

        dataProvider.getRequestHeaders().remove(HttpHeaders.HOST);
        dataProvider.getRequestHeaders().remove(HttpHeaders.AUTHORIZATION);
        dataProvider.getRequestHeaders().remove(HttpHeaders.USER_AGENT);
        dataProvider.getRequestHeaders().remove(HttpHeaders.CONTENT_TYPE);

        dataProvider.getResponseHeaders().remove(HttpHeaders.CONTENT_TYPE);

        params.put("request_body", null);
        params.put("request_body_size", null);
        params.put("request_total_size", null);
        params.put("response_body", null);
        params.put("response_body_size", null);
        params.put("response_total_size", null);

        dataProvider.getRequestPayload().ifPresent(rp ->
        {
            final boolean storeRequestData = dataProvider.getPredicateConfig()
                    .map(PredicateConfig::isLogRequestBody)
                    .orElse(false);

            params.put("request_body", storeRequestData ? IoUtil.readAllBytes(rp.data()) : null);
            params.put("request_body_size", rp.bodyLength());
            params.put("request_total_size", rp.totalLength());
        });
        dataProvider.getResponsePayload().ifPresent(rp ->
        {
            final boolean storeResponseData = dataProvider.getPredicateConfig()
                    .map(PredicateConfig::isLogResponseBody)
                    .orElse(false);

            params.put("response_body", storeResponseData ? IoUtil.readAllBytes(rp.data()) : null);
            params.put("response_body_size", rp.bodyLength());
            params.put("response_total_size", rp.totalLength());
        });

        params.put("request_headers", flattenMap(dataProvider.getRequestHeaders()));
        params.put("response_headers", flattenMap(dataProvider.getResponseHeaders()));
        tpl.update("""
                INSERT INTO log (
                  timestamp, route_id, route_uri, gateway_request_id, method, path,
                  response_time, request_body_size, response_body_size, request_total_size,
                  response_total_size, status, is_error, user_claim, realm_claim, host,
                  request_content_type, response_content_type, user_agent,
                  request_headers, response_headers, request_body, response_body)
                VALUES(
                  :timestamp, :route_id, :route_uri, :gateway_request_id, :method, :path,
                  :duration, :request_body_size, :response_body_size,
                  :request_total_size, :response_total_size, :status, :is_error, :user_claim, :realm_claim,
                  :host, :request_content_type, :response_content_type, :user_agent,
                  :request_headers, :response_headers,
                  :request_body, :response_body)""", params);
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
