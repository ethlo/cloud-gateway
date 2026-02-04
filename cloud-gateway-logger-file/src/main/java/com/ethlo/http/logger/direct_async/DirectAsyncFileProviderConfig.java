package com.ethlo.http.logger.direct_async;

import java.nio.file.Path;

import com.ethlo.http.logger.BaseProviderConfig;

public class DirectAsyncFileProviderConfig extends BaseProviderConfig
{
    private final String pattern;
    private final Path storageDirectory;

    public DirectAsyncFileProviderConfig(final boolean enabled, final String pattern, final Path storageDirectory)
    {
        super(enabled);
        this.pattern = pattern;
        this.storageDirectory = storageDirectory;
    }

    public String pattern()
    {
        return pattern;
    }

    public Path storageDirectory()
    {
        return storageDirectory;
    }
}
