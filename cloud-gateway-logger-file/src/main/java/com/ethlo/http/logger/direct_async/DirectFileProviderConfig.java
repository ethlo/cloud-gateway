package com.ethlo.http.logger.direct_async;

import java.nio.file.Path;

import com.ethlo.http.logger.BaseProviderConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Valid
public class DirectFileProviderConfig extends BaseProviderConfig
{
    @NotNull
    private final String pattern;

    @NotNull
    private final Path storageDirectory;

    public DirectFileProviderConfig(final boolean enabled, final String pattern, final Path storageDirectory)
    {
        super(enabled);
        this.pattern = pattern;
        this.storageDirectory = storageDirectory;
    }

    public String pattern()
    {
        return pattern;
    }

    @NotNull
    public Path storageDirectory()
    {
        return storageDirectory;
    }
}
