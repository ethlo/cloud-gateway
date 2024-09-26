package com.ethlo.http.logger.file;

import java.nio.file.Path;

import com.ethlo.http.logger.BaseProviderConfig;

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
