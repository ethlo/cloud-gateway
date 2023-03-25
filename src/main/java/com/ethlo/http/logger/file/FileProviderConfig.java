package com.ethlo.http.logger.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ethlo.http.logger.BaseProviderConfig;

@ConfigurationProperties("logging.providers.file")
public class FileProviderConfig extends BaseProviderConfig
{
    private final String pattern;

    protected FileProviderConfig(final boolean enabled, final String pattern)
    {
        super(enabled);
        this.pattern = pattern;
    }

    public String getPattern()
    {
        return pattern;
    }
}
