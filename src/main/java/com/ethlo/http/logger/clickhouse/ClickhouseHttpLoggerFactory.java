package com.ethlo.http.logger.clickhouse;

import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.clickhouse.jdbc.internal.ClickHouseJdbcUrlParser;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.HttpLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Component
public class ClickhouseHttpLoggerFactory implements HttpLoggerFactory
{
    @Override
    public String getName()
    {
        return "clickhouse";
    }

    @Override
    public HttpLogger getInstance(GenericApplicationContext applicationContext, final Map<String, Object> configuration)
    {
        final ClickHouseProviderConfig clickHouseProviderConfig = load(configuration, ClickHouseProviderConfig.class);

        // Register additional beans
        flyway(clickHouseProviderConfig);
        final NamedParameterJdbcTemplate tpl = namedParameterJdbcTemplate(clickHouseProviderConfig);
        applicationContext.getBeanFactory().initializeBean(clickHouseStatsEndpoint(tpl), "clickHouseStatsEndpoint");

        return new ClickHouseLogger(tpl);
    }

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate(ClickHouseProviderConfig clickHouseProviderConfig)
    {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(clickHouseProviderConfig.getUrl());
        final DataSource dataSource = new HikariDataSource(config);
        return new NamedParameterJdbcTemplate(dataSource);
    }

    public ClickHouseStatsEndpoint clickHouseStatsEndpoint(NamedParameterJdbcTemplate tpl)
    {
        return new ClickHouseStatsEndpoint(tpl, "log");
    }


    private void flyway(ClickHouseProviderConfig clickHouseProviderConfig)
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
    }

    private String extractSchema(String url)
    {
        try
        {
            final ClickHouseJdbcUrlParser.ConnectionInfo ch = ClickHouseJdbcUrlParser.parse(url, new Properties());
            return ch.getNodes().getNodes().getFirst().getDatabase().orElseThrow(() -> new IllegalStateException("No database defined in url: " + url));
        }
        catch (SQLException e)
        {
            throw new DataAccessResourceFailureException("Unable to extract schema from connection info", e);
        }
    }
}
