package com.ethlo.http.logger.clickhouse;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.clickhouse.data.value.UnsignedLong;

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
        result.putAll(getStoredDataSizes());
        return result;
    }

    public Map<String, Object> getTableStats()
    {
        final String sql = """
                select
                       sum(rows)                        as row_count,
                       max(modification_time)           as latest_modification_timestamp,
                       sum(data_compressed_bytes)       as data_compressed_size,
                       sum(data_uncompressed_bytes)     as data_uncompressed_size,
                       any(engine)                      as engine,
                       sum(primary_key_bytes_in_memory) as primary_key_bytes_in_mem_size
                from system.parts
                where active and database = currentDatabase() and `table` = :table
                group by table""";
        return tpl.queryForMap(sql, Map.of("table", tableName));
    }

    public Map<String, Object> getStoredDataSizes()
    {
        final String sql = """
                select sum(length(request_raw)) as request_raw_size, 
                sum(length(response_raw)) as response_raw_size, 
                sum(length(response_body)) as response_body_size, 
                sum(length(request_body)) as request_body_size from log""";
        return tpl.queryForMap(sql, Collections.emptyMap());
    }

    public List<Map<String, Object>> getInsertStats(Duration interval, final int intervalCount)
    {
        final String sql = """
                SELECT
                toStartOfInterval(event_time, INTERVAL :interval SECOND) AS event_time, count() as count,
                sum(query_duration_ms) / CAST(count() as double) as avg_insert_duration
                FROM system.query_log
                WHERE type = 'QueryFinish'
                AND query_kind = 'Insert'
                AND (event_time > (now() - toIntervalSecond(:interval * :intervalCount)))
                GROUP BY event_time
                ORDER BY event_time DESC WITH FILL FROM toStartOfInterval(now(), INTERVAL :interval SECOND) to (toStartOfInterval(now(), INTERVAL :interval SECOND) - toIntervalSecond(:interval * :intervalCount)) STEP toIntervalSecond(-:interval),
                count""";
        final List<Map<String, Object>> result = tpl.queryForList(sql, Map.of("interval", interval.toSeconds(), "intervalCount", intervalCount));
        final long max = result.stream().mapToLong(m -> ((UnsignedLong) m.getOrDefault("count", UnsignedLong.valueOf(0))).longValue()).max().orElse(1);
        result.forEach(map ->
        {
            final double percentage = (((UnsignedLong) map.getOrDefault("count", UnsignedLong.valueOf(0))).longValue() / (double) max) * 100;
            map.put("count_percentage", percentage);
        });
        return result;
    }
}
