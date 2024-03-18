package com.ethlo.http;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.ethlo.http.logger.clickhouse.ClickHouseStatsEndpoint;
import com.ethlo.http.util.IoUtil;

@Component
@Endpoint(id = "status")
public class CloudGatewayStatusEndpoint
{
    private final PebbleRenderer pebbleRenderer = new PebbleRenderer(false);
    private final ClickHouseStatsEndpoint clickHouseStatsEndpoint;
    private final IOStatusEndpoint ioStatusEndpoint;

    public CloudGatewayStatusEndpoint(ClickHouseStatsEndpoint clickHouseStatsEndpoint, IOStatusEndpoint ioStatusEndpoint)
    {
        this.clickHouseStatsEndpoint = clickHouseStatsEndpoint;
        this.ioStatusEndpoint = ioStatusEndpoint;
    }

    @ReadOperation(produces = MediaType.TEXT_HTML_VALUE)
    public String getInfo() throws IOException
    {
        final Map<String, Object> model = new LinkedHashMap<>();
        model.put("clickhouse", preProcess(clickHouseStatsEndpoint.logTableStats()));
        model.put("clickhouse_minute_interval_count", 10);
        model.put("clickhouse_minute_queries", clickHouseStatsEndpoint.getInsertStats(Duration.ofMinutes(1), 10).stream().map(this::preProcess).toList());

        model.put("clickhouse_hour_interval_count", 24);
        model.put("clickhouse_hour_queries", clickHouseStatsEndpoint.getInsertStats(Duration.ofHours(1), 24).stream().map(this::preProcess).toList());

        model.put("clickhouse_day_interval_count", 7);
        model.put("clickhouse_day_queries", clickHouseStatsEndpoint.getInsertStats(Duration.ofDays(1), 10).stream().map(this::preProcess).toList());

        model.put("logio", preProcess(ioStatusEndpoint.getLogio()));
        return pebbleRenderer.renderFromTemplate(model, "info.tpl.html", Locale.ENGLISH, false);
    }

    private Map<String, Object> preProcess(Map<String, Object> map)
    {
        final Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) ->
        {
            if (k.endsWith("size"))
            {
                result.put(k, IoUtil.formatSize(Long.parseLong(v.toString())));
            }
            else if (k.endsWith("count"))
            {
                result.put(k, String.format("%,d\n", Long.parseLong(v.toString())));
            }
            else if (k.endsWith("duration"))
            {
                final double ms = Double.parseDouble(v.toString());
                result.put(k, String.format("%,.3f", ms));
            }
            else
            {
                result.put(k, v);
            }
        });
        return result;
    }

    @ReadOperation(produces = "text/css")
    public String getCss(@Selector final String css) throws IOException
    {
        return new String(new ClassPathResource("static/pico.amber.min.css").getInputStream().readAllBytes());
    }
}