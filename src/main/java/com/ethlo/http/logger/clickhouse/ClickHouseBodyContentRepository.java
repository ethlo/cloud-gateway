package com.ethlo.http.logger.clickhouse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ethlo.http.logger.BodyContentRepository;

public class ClickHouseBodyContentRepository implements BodyContentRepository
{
    private final NamedParameterJdbcTemplate tpl;

    public ClickHouseBodyContentRepository(final NamedParameterJdbcTemplate tpl)
    {
        this.tpl = tpl;
    }

    @Override
    public void saveRequest(final String requestId, final Resource requestBody)
    {
        // Handled via logger
    }

    @Override
    public void saveResponse(final String requestId, final Resource responseBody)
    {
        // Handled via logger
    }

    @Override
    public Optional<Resource> getRequestData(final String requestId)
    {
        return getByteArrayResource(requestId, "request_body");
    }

    private Optional<Resource> getByteArrayResource(String requestId, final String columnName)
    {
        final List<byte[]> result = tpl.queryForList("SELECT " + columnName + " FROM log WHERE gateway_request_id = :request_id", Map.of("request_id", requestId), byte[].class);
        return Optional.ofNullable(result.isEmpty() ? null : result.get(0)).map(ByteArrayResource::new);
    }

    @Override
    public Optional<Resource> getResponseData(final String requestId)
    {
        return getByteArrayResource(requestId, "response_body");
    }
}
