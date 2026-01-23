package com.ethlo.http.logger.clickhouse;

import com.ethlo.http.logger.BaseProviderConfig;

public class ClickHouseProviderConfig extends BaseProviderConfig
{
    private final String url;
    private final String username;
    private final String password;
    private final String connectionInitSql;

    public ClickHouseProviderConfig(final boolean enabled, final String url, final String username, final String password, final String connectionInitSql)
    {
        super(enabled);
        this.url = url;
        this.username = username;
        this.password = password;
        this.connectionInitSql = connectionInitSql;
    }

    public String getUrl()
    {
        return url;
    }

    public String getUsername()
    {
        return username;
    }

    public String getPassword()
    {
        return password;
    }

    public String getConnectionInitSql()
    {
        return connectionInitSql;
    }
}
