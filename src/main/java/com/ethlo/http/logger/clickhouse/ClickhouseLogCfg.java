package com.ethlo.http.logger.clickhouse;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ConditionalOnProperty("logging.provider.clickhouse")
@Configuration
public class ClickhouseCfg
{
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource)
    {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public ClickHouseLogger clickHouseLogger()
    {
        return new ClickHouseLogger();
    }

    @Bean
    public Dialect dialect()
    {
        return new ClickhouseDialect();
    }
}
