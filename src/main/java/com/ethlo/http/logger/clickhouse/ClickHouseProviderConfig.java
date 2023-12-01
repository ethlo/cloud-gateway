package com.ethlo.http.logger.clickhouse;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ethlo.http.logger.BaseProviderConfig;

import org.springframework.cloud.context.config.annotation.RefreshScope;

@RefreshScope
@ConfigurationProperties("http-logging.providers.clickhouse")
public class ClickHouseProviderConfig extends BaseProviderConfig
{
    private final String url;

    public ClickHouseProviderConfig(final boolean enabled, final String url)
    {
        super(enabled);
        this.url = url;
    }

    public String getUrl()
    {
        return url;
    }
}
