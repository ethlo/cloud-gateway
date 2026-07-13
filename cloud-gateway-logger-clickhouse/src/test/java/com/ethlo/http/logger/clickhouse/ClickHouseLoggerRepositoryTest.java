package com.ethlo.http.logger.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
class ClickHouseLoggerRepositoryTest
{
    private static final ClickHouseContainer CLICKHOUSE;

    static
    {
        CLICKHOUSE = new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:25.11"))
                .withDatabaseName("http_log")
                .withUsername("default")
                .withPassword("clickhouse_pass");

        Startables.deepStart(Stream.of(CLICKHOUSE)).join();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ClickHouseProviderConfig providerConfig;

    @Test
    void testRequestResponseHeaderLogging()
    {
        // Verify config is loaded successfully (optional sanity check)
        assertThat(providerConfig.isEnabled()).isTrue();
        assertThat(providerConfig.getUrl()).isEqualTo(CLICKHOUSE.getJdbcUrl());

        final NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        final ClickHouseLoggerRepository clickHouseLoggerRepository = new ClickHouseLoggerRepository(namedParameterJdbcTemplate);

        Map<String, Object> params = new HashMap<>();

        // Core Request Info
        params.put("timestamp", OffsetDateTime.now());
        params.put("route_id", "my-upstream-service");
        params.put("route_uri", "http://backend-service:8080");
        params.put("gateway_request_id", "req-12345-abcde");
        params.put("method", "POST");
        params.put("path", "/api/v1/users");
        params.put("host", "api.example.com");

        params.put("duration", 145L); // milliseconds
        params.put("request_body_size", 2048L);
        params.put("response_body_size", 512L);
        params.put("request_total_size", 2500L); // headers + body
        params.put("response_total_size", 800L); // headers + body

        params.put("status", 201);
        params.put("is_error", false);

        params.put("user_claim", "john.doe");
        params.put("realm_claim", "internal-staff");

        params.put("request_content_type", "application/json");
        params.put("response_content_type", "application/json");
        params.put("user_agent", "Mozilla/5.0 (IntegrationTest/1.0)");

        params.put("request_headers", Map.of(
                        "Host", "api.example.com",
                        "Content-Type", "application/json",
                        "X-Forwarded-For", "192.168.1.1, 94.22.33.193",
                        "Authorization", "Bearer eyJhb..."
                )
        );

        params.put("response_headers", Map.of(
                        "Content-Type", "application/json",
                        "Content-Length", "512",
                        "X-RateLimit-Remaining", "99"
                )
        );

        params.put("request_body", "{\"username\": \"john.doe\", \"email\": \"john@example.com\"}".getBytes(StandardCharsets.UTF_8));
        params.put("response_body", "{\"id\": 99, \"status\": \"created\"}".getBytes(StandardCharsets.UTF_8));
        params.put("request_raw", "POST /api/v1/users HTTP/1.1\r\nHost: api.example.com...".getBytes(StandardCharsets.UTF_8));
        params.put("response_raw", "HTTP/1.1 201 Created\r\nContent-Type: application/json...".getBytes(StandardCharsets.UTF_8));

        params.put("exception_type", null);
        params.put("exception_message", null);

        clickHouseLoggerRepository.insert(params);

        // Fetch the row using the unique gateway request ID
        final List<Map<String, Object>> results = namedParameterJdbcTemplate.getJdbcTemplate().queryForList(
                "SELECT * FROM log WHERE gateway_request_id = ?", "req-12345-abcde");

        assertThat(results).hasSize(1);
        Map<String, Object> row = results.getFirst();

        assertThat(row).containsEntry("method", "POST")
                .containsEntry("path", "/api/v1/users")
                .containsKey("timestamp");

        assertThat(((Number) row.get("response_time")).longValue()).isEqualTo(145L);
        assertThat(((Number) row.get("request_body_size")).longValue()).isEqualTo(2048L);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(201);

        assertThat(row).containsEntry("is_error", (short) 0);

        @SuppressWarnings("unchecked")
        Map<String, String> requestHeaders = (Map<String, String>) row.get("request_headers");
        assertThat(requestHeaders)
                .containsEntry("Host", "api.example.com")
                .containsEntry("X-Forwarded-For", "192.168.1.1, 94.22.33.193")
                .containsEntry("Authorization", "Bearer eyJhb...");

        @SuppressWarnings("unchecked")
        Map<String, String> responseHeaders = (Map<String, String>) row.get("response_headers");
        assertThat(responseHeaders)
                .containsEntry("X-RateLimit-Remaining", "99");

        assertThat(row).containsEntry("request_body", "{\"username\": \"john.doe\", \"email\": \"john@example.com\"}")
                .containsEntry("response_body", "{\"id\": 99, \"status\": \"created\"}");
    }

    @EnableAutoConfiguration(exclude = DataJdbcRepositoriesAutoConfiguration.class)
    @SpringBootConfiguration
    public static class TestCfg
    {
        /**
         * Create the custom config bean, populated with Testcontainers data
         */
        @Bean
        public ClickHouseProviderConfig clickHouseProviderConfig()
        {
            return new ClickHouseProviderConfig(
                    true, // enabled
                    CLICKHOUSE.getJdbcUrl(),
                    CLICKHOUSE.getUsername(),
                    CLICKHOUSE.getPassword(),
                    null // connectionInitSql, update if you need a specific table setup script
            );
        }

        @Bean
        public DataSource dataSource(ClickHouseProviderConfig config)
        {
            return DataSourceBuilder.create()
                    .driverClassName("com.clickhouse.jdbc.ClickHouseDriver")
                    .url(config.getUrl())
                    .username(config.getUsername())
                    .password(config.getPassword())
                    .build();
        }
    }
}