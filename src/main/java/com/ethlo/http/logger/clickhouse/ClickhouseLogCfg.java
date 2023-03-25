package com.ethlo.http.logger.clickhouse;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@ConditionalOnProperty("logging.providers.clickhouse.enabled")
@Configuration
public class ClickhouseLogCfg
{
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(ClickHouseProviderConfig clickHouseProviderConfig)
    {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(clickHouseProviderConfig.getUrl());
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
