package com.ethlo.http.logger.clickhouse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerEndpoint(id = "clickhouse")
public class ClickHouseStatsEndpoint
{
    private final NamedParameterJdbcTemplate tpl;
    private final String tableName;

    public ClickHouseStatsEndpoint(final NamedParameterJdbcTemplate tpl, final String tableName)
    {
        this.tpl = tpl;
        this.tableName = tableName;
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody
    Map<String, Object> logTableStats()
    {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(getTableStats());
        result.putAll(getBodySizes());
        return result;
    }

    private Map<String, Object> getTableStats()
    {
        final String sql = """
                select
                       sum(bytes)                       as size,
                       sum(rows)                        as rows,
                       max(modification_time)           as latest_modification,
                       sum(bytes)                       as bytes_size,
                       any(engine)                      as engine,
                       sum(primary_key_bytes_in_memory) as primary_keys_size
                from system.parts
                where active and database = currentDatabase() and table = :table
                group by table
                order by bytes_size desc""";
        return tpl.queryForMap(sql, Map.of("table", tableName));
    }

    private Map<String, Object> getBodySizes()
    {
        final String sql = "select sum(length(response_body)) as response_body_size, sum(length(request_body)) as request_body_size from log";
        return tpl.queryForMap(sql, Collections.emptyMap());
    }
}
