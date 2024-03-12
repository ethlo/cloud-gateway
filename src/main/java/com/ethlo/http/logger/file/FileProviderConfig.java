package com.ethlo.http.logger.file;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import com.ethlo.http.logger.BaseProviderConfig;

@RefreshScope
@ConfigurationProperties("http-logging.providers.file")
public class FileProviderConfig extends BaseProviderConfig
{
    private final String pattern;
    private final Path storageDirectory;

    protected FileProviderConfig(final boolean enabled, final String pattern, final Path storageDirectory)
    {
        super(enabled);
        this.pattern = pattern;
        this.storageDirectory = storageDirectory;
    }

    public String getPattern()
    {
        return pattern;
    }

    public Path getStorageDirectory()
    {
        return storageDirectory;
    }
}
