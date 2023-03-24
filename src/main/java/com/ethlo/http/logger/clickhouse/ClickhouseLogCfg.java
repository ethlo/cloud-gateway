package com.ethlo.http.logger.clickhouse;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@ConditionalOnProperty("logging.provider.clickhouse.enabled")
@Configuration
public class ClickhouseLogCfg
{
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(@Value("${logging.provider.clickhouse.url}") final String url)
    {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        final DataSource dataSource = new HikariDataSource(config);
        return new NamedParameterJdbcTemplate(dataSource);
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
}
