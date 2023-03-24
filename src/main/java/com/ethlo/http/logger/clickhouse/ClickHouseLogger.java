package com.ethlo.http.logger.clickhouse;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import com.ethlo.http.logger.HttpLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.ethlo.http.logger.file.AccessLogTemplateRenderer;

public class ClickHouseLogger implements HttpLogger
{
    private final NamedParameterJdbcTemplate tpl;

    public ClickHouseLogger(final NamedParameterJdbcTemplate tpl)
    {
        this.tpl = tpl;
    }

    @Override
    public void accessLog(final Map<String, Object> data, final BufferedInputStream requestData, final BufferedInputStream responseData)
    {
        data.put("request_content", getBytes(requestData));
        data.put("response_content", getBytes(responseData));
        tpl.update("""
                INSERT INTO log (
                  timestamp, gateway_request_id, method, path,
                  response_time, request_size, response_size,
                  status, request_headers, response_headers, request_content, response_content) 
                VALUES(
                  :timestamp, :gateway_request_id, :method, :path, 
                  :response_time, :request_size, :response_size, 
                  :status, :request_headers, :response_headers, 
                  :request_content, :response_content)""", data);
    }

    private byte[] getBytes(BufferedInputStream requestData)
    {
        if (requestData == null)
        {
            return null;
        }

        try
        {
            return requestData.readAllBytes();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
