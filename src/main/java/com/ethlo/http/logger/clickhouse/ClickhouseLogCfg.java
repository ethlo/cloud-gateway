package com.ethlo.http.logger.clickhouse;

import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.clickhouse.jdbc.internal.ClickHouseJdbcUrlParser;
import com.ethlo.http.logger.BodyContentRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@ConditionalOnProperty("http-logging.providers.clickhouse.enabled")
@Configuration
public class ClickhouseLogCfg
{
    @Bean
    Flyway flyway(ClickHouseProviderConfig clickHouseProviderConfig)
    {
        final Flyway flyway = Flyway.configure()
                .defaultSchema(extractSchema(clickHouseProviderConfig.getUrl()))
                .locations("db/clickhouse/migration")
                .baselineOnMigrate(true)
                .dataSource(
                        clickHouseProviderConfig.getUrl(),
                        clickHouseProviderConfig.getUsername(),
                        clickHouseProviderConfig.getPassword()
                ).load();
        flyway.migrate();
        return flyway;
    }

    private String extractSchema(String url)
    {
        try
        {
            final ClickHouseJdbcUrlParser.ConnectionInfo ch = ClickHouseJdbcUrlParser.parse(url, new Properties());
            return ch.getNodes().getNodes().get(0).getDatabase().orElseThrow(() -> new IllegalStateException("No database defined in url: " + url));
        }
        catch (SQLException e)
        {
            throw new DataAccessResourceFailureException("Unable to extract schema from connection info", e);
        }
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(ClickHouseProviderConfig clickHouseProviderConfig)
    {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(clickHouseProviderConfig.getUrl());
        final DataSource dataSource = new HikariDataSource(config);
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public BodyContentRepository bodyContentRepository(NamedParameterJdbcTemplate tpl)
    {
        return new ClickHouseBodyContentRepository(tpl);
    }

    @Bean
    public ClickHouseLogger clickHouseLogger(NamedParameterJdbcTemplate tpl)
    {
        return new ClickHouseLogger(tpl);
    }

    @Bean
    public Dialect dialect()
    {
        return new ClickhouseDialect();
    }

    @Bean
    public ClickHouseStatsEndpoint clickHouseStatsEndpoint(NamedParameterJdbcTemplate tpl)
    {
        return new ClickHouseStatsEndpoint(tpl, "log");
    }
}
