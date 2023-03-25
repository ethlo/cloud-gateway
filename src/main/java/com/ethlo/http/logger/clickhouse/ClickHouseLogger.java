package com.ethlo.http.logger.clickhouse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;
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
                  timestamp, gateway_request_id, method, path,
                  response_time, request_body_size, response_body_size,
                  status, request_headers, response_headers, request_body, response_body)
                VALUES(
                  :timestamp, :gateway_request_id, :method, :path, 
                  :duration, :request_body_size, :response_body_size, 
                  :status, :request_headers, :response_headers, 
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
