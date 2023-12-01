package com.ethlo.http.logger.file;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ethlo.http.logger.BaseProviderConfig;

import org.springframework.cloud.context.config.annotation.RefreshScope;

@RefreshScope
@ConfigurationProperties("http-logging.providers.file")
public class FileProviderConfig extends BaseProviderConfig
{
    private final String pattern;
    private final Path bodyStorageDirectory;

    protected FileProviderConfig(final boolean enabled, final String pattern, final Path bodyStorageDirectory)
    {
        super(enabled);
        this.pattern = pattern;
        this.bodyStorageDirectory = bodyStorageDirectory;
    }

    public String getPattern()
    {
        return pattern;
    }

    public Path getBodyStorageDirectory()
    {
        return bodyStorageDirectory;
    }
}
