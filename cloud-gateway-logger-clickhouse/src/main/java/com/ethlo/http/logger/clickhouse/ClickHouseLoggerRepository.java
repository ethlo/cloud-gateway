package com.ethlo.http.logger.clickhouse;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class ClickHouseLoggerRepository
{
    private final NamedParameterJdbcTemplate tpl;

    public ClickHouseLoggerRepository(final NamedParameterJdbcTemplate tpl)
    {
        this.tpl = tpl;
    }

    public void insert(Map<String, Object> params)
    {
        params.entrySet().forEach(entry ->
        {
            if (entry.getValue() instanceof byte[] bytes)
            {
                entry.setValue(new ByteArrayInputStream(bytes));
            }
        });

        tpl.update("""                    
                        INSERT INTO log (
                          timestamp, route_id, route_uri, gateway_request_id, method, path,
                          response_time, request_body_size, response_body_size, request_total_size,
                          response_total_size, status, is_error, user_claim, realm_claim, host,
                          request_content_type, response_content_type, user_agent,
                          request_headers, response_headers, request_body, response_body, request_raw, response_raw, exception_type, exception_message)
                        VALUES(
                          :timestamp, :route_id, :route_uri, :gateway_request_id, :method, :path,
                          :duration, :request_body_size, :response_body_size,
                          :request_total_size, :response_total_size, :status, :is_error, :user_claim, :realm_claim,
                          :host, :request_content_type, :response_content_type, :user_agent,
                          :request_headers, :response_headers,
                          :request_body, :response_body, :request_raw, :response_raw, :exception_type, :exception_message)""",
                params
        );
    }
}
