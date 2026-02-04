package com.ethlo.http;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@Import(CloudGatewayApplication.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class BaseTest
{
    protected static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    private static final ClickHouseContainer CLICKHOUSE;

    static
    {
        CLICKHOUSE = new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:25.11"))
                .withDatabaseName("http_log")
                .withUsername("default")
                .withPassword("clickhouse_pass");

        Startables.deepStart(Stream.of(CLICKHOUSE)).join();
    }

    @DynamicPropertySource
    public static void configureClickHouseProperties(DynamicPropertyRegistry registry)
    {
        registry.add("http-logging.providers.clickhouse.url", CLICKHOUSE::getJdbcUrl);
        registry.add("http-logging.providers.clickhouse.username", CLICKHOUSE::getUsername);
        registry.add("http-logging.providers.clickhouse.password", CLICKHOUSE::getPassword);
    }
}
