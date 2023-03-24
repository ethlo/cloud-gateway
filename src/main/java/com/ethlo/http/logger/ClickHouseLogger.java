package com.ethlo.http.logger;

import java.io.InputStream;
import java.util.Map;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClickHouseLogger implements HttpLogger
{
    private final NamedParameterJdbcTemplate tpl;

    public ClickHouseLogger(final NamedParameterJdbcTemplate tpl)
    {
        this.tpl = tpl;
    }

    @Override
    public void completed(final ServerHttpRequest request, final ServerHttpResponse response, final InputStream inputStream, final InputStream stream)
    {

    }

    @Override
    public void accessLog(final Map<String, Object> data)
    {
        tpl.update("""
                INSERT INTO log (
                  timestamp, gateway_request_id, method, path,
                  response_time, request_size, response_size,
                  status, request_headers, response_headers) 
                VALUES(
                  :timestamp, :gateway_request_id, :method, :path, 
                  :response_time, :request_size, :response_size, 
                  :status, :request_headers, :response_headers)""", data);
    }
}
