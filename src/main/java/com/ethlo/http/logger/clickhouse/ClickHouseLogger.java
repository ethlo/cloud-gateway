package com.ethlo.http.logger.clickhouse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.processors.auth.RealmUser;
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

        params.put("request_body", null);
        params.put("request_body_size", 0);
        params.put("response_body", null);
        params.put("response_body_size", 0);

        params.put("realm_claim", dataProvider.getUser().map(RealmUser::realm).orElse(null));
        params.put("user_claim", dataProvider.getUser().map(RealmUser::username).orElse(null));

        params.put("host", dataProvider.getRequestHeaders().get(HttpHeaders.HOST));
        params.put("user_agent", dataProvider.getRequestHeaders().get(HttpHeaders.USER_AGENT));

        params.put("request_content_type", Optional.ofNullable(dataProvider.getRequestHeaders().getContentType()).map(MediaType::toString).orElse(null));
        params.put("response_content_type", Optional.ofNullable(dataProvider.getResponseHeaders().getContentType()).map(MediaType::toString).orElse(null));

        // Remove headers already captured in dedicated columns
        dataProvider.requestHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getRequestHeaders()));
        dataProvider.responseHeaders(HttpHeaders.writableHttpHeaders(dataProvider.getResponseHeaders()));

        dataProvider.getRequestHeaders().remove(HttpHeaders.HOST);
        dataProvider.getRequestHeaders().remove(HttpHeaders.AUTHORIZATION);
        dataProvider.getRequestHeaders().remove(HttpHeaders.USER_AGENT);
        dataProvider.getRequestHeaders().remove(HttpHeaders.CONTENT_TYPE);

        dataProvider.getResponseHeaders().remove(HttpHeaders.CONTENT_TYPE);

        dataProvider.getRequestPayload().ifPresent(rp ->
        {
            params.put("request_body", IoUtil.readAllBytes(rp.data()));
            params.put("request_body_size", rp.length());
        });
        dataProvider.getResponsePayload().ifPresent(rp ->
        {
            params.put("response_body", IoUtil.readAllBytes(rp.data()));
            params.put("response_body_size", rp.length());
        });

        params.put("request_headers", flattenMap(dataProvider.getRequestHeaders()));
        params.put("response_headers", flattenMap(dataProvider.getResponseHeaders()));
        tpl.update("""
                INSERT INTO log (
                  timestamp, route_id, route_uri, gateway_request_id, method, path,
                  response_time, request_body_size, response_body_size,
                  status, is_error, user_claim, realm_claim, host,
                  request_content_type, response_content_type, user_agent,
                  request_headers, response_headers, request_body, response_body)
                VALUES(
                  :timestamp, :route_id, :route_uri, :gateway_request_id, :method, :path, 
                  :duration, :request_body_size, :response_body_size, 
                  :status, :is_error, :user_claim, :realm_claim, 
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
