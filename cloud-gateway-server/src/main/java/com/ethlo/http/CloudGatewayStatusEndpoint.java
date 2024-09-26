package com.ethlo.http;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.logger.clickhouse.ClickHouseStatsEndpoint;
import com.ethlo.http.util.IoUtil;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;

@Component
@Endpoint(id = "status")
@RefreshScope
public class CloudGatewayStatusEndpoint
{
    private final PebbleRenderer pebbleRenderer = new PebbleRenderer(false);
    private final MeterRegistry meterRegistry;
    private final HttpLoggingConfiguration httpLoggingConfiguration;
    private final ClickHouseStatsEndpoint clickHouseStatsEndpoint;
    private final IOStatusEndpoint ioStatusEndpoint;

    public CloudGatewayStatusEndpoint(final MeterRegistry meterRegistry,
                                      final HttpLoggingConfiguration httpLoggingConfiguration,
                                      @Autowired(required = false) ClickHouseStatsEndpoint clickHouseStatsEndpoint,
                                      @Autowired(required = false) IOStatusEndpoint ioStatusEndpoint)
    {
        this.meterRegistry = meterRegistry;
        this.httpLoggingConfiguration = httpLoggingConfiguration;
        this.clickHouseStatsEndpoint = clickHouseStatsEndpoint;
        this.ioStatusEndpoint = ioStatusEndpoint;
    }

    @ReadOperation(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getInfoJson()
    {
        final Map<String, Object> model = new LinkedHashMap<>();

        model.put("capture_config", httpLoggingConfiguration.getCapture());

        final Map<String, Double> usedMemory = getMetrics("jvm.memory.used");
        final Map<String, Double> committedMemory = getMetrics("jvm.memory.committed");
        final Map<String, Double> maxMemory = getMetrics("jvm.memory.max");
        model.put("memory_summary", Map.of(
                "used", sumMem(usedMemory),
                "committed", sumMem(committedMemory),
                "max", sumMem(maxMemory)
        ));
        model.put("memory", overlay(usedMemory, committedMemory, maxMemory));

        if (clickHouseStatsEndpoint != null)
        {
            model.put("clickhouse", preProcess(clickHouseStatsEndpoint.logTableStats()));
            model.put("clickhouse_minute_interval_count", 10);
            model.put("clickhouse_minute_queries", clickHouseStatsEndpoint.getInsertStats(Duration.ofMinutes(1), 10).stream().map(this::preProcess).toList());

            model.put("clickhouse_hour_interval_count", 24);
            model.put("clickhouse_hour_queries", clickHouseStatsEndpoint.getInsertStats(Duration.ofHours(1), 24).stream().map(this::preProcess).toList());

            model.put("clickhouse_day_interval_count", 7);
            model.put("clickhouse_day_queries", clickHouseStatsEndpoint.getInsertStats(Duration.ofDays(1), 10).stream().map(this::preProcess).toList());
        }

        if (ioStatusEndpoint != null)
        {
            model.put("logio", preProcess(ioStatusEndpoint.getLogio()));
        }
        return model;
    }

    @ReadOperation(produces = MediaType.TEXT_HTML_VALUE)
    public String getInfoHtml() throws IOException
    {
        final Map<String, Object> model = getInfoJson();
        return pebbleRenderer.renderFromTemplate(model, "info.tpl.html", Locale.ENGLISH);
    }

    private Map<String, Memory> overlay(Map<String, Double> usedMemory, Map<String, Double> committedMemory, Map<String, Double> totalMemory)
    {
        final Map<String, Memory> result = new LinkedHashMap<>();
        usedMemory.forEach((k, v) ->
                result.put(k, new Memory(v, committedMemory.get(k), totalMemory.get(k))));
        return result;
    }

    private double sumMem(Map<String, Double> memTypes)
    {
        return memTypes.values()
                .stream()
                .filter(d -> d > 0)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private Map<String, Double> getMetrics(@Nonnull final String key)
    {
        final List<Meter> byKey = meterRegistry.getMeters()
                .stream()
                .filter(m -> key.equals(m.getId().getName()))
                .toList();
        return new TreeMap<>(byKey
                .stream()
                .collect(Collectors.toMap(m ->
                        m.getId().getTags().stream()
                                .map(t -> t.getKey() + ":" + t.getValue())
                                .collect(Collectors.joining(", ")), m -> m.measure()
                        .iterator().next().getValue())));
    }

    private Map<String, Object> preProcess(Map<String, Object> map)
    {
        final Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) ->
        {
            if (k.endsWith("size"))
            {
                result.put(k, Optional.ofNullable(v).map(value -> IoUtil.formatSize(Long.parseLong(value.toString()))).orElse(null));
            }
            else if (k.endsWith("count"))
            {
                result.put(k, Optional.ofNullable(v).map(value -> String.format("%,d\n", Long.parseLong(value.toString()))).orElse(null));
            }
            else if (k.endsWith("duration"))
            {
                result.put(k, Optional.ofNullable(v).map(value -> String.format("%,.3f", Double.parseDouble(value.toString()) / 1000)).orElse(null));
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
        return new String(new ClassPathResource("static/pico.min.css").getInputStream().readAllBytes());
    }

    public record Memory(Double used, Double committed, Double max)
    {

    }
}