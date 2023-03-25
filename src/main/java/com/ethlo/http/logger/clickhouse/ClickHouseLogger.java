package com.ethlo.http.logger.clickhouse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.model.PayloadProvider;
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
        params.put("request_content", dataProvider.getRequestPayload().map(rp ->
        {
            params.put("request_size", rp.length());
            return rp;
        }).map(PayloadProvider::data).map(IoUtil::readAllBytes).orElse(null));
        params.put("response_content", dataProvider.getResponsePayload().map(rp ->
        {
            params.put("response_size", rp.length());
            return rp;
        }).map(PayloadProvider::data).map(IoUtil::readAllBytes).orElse(null));
        params.put("request_headers", flattenMap(dataProvider.getRequestHeaders()));
        params.put("response_headers", flattenMap(dataProvider.getResponseHeaders()));
        tpl.update("""
                INSERT INTO log (
                  timestamp, gateway_request_id, method, path,
                  response_time, request_size, response_size,
                  status, request_headers, response_headers, request_content, response_content)
                VALUES(
                  :timestamp, :gateway_request_id, :method, :path, 
                  :duration/1000000, :request_size, :response_size, 
                  :status, :request_headers, :response_headers, 
                  :request_content, :response_content)""", params);
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
