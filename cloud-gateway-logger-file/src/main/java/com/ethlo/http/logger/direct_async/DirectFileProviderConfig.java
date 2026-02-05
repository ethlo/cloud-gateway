package com.ethlo.http.logger.direct_async;

import java.nio.file.Path;

import org.springframework.util.unit.DataSize;

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

    private final DataSize maxRolloverSize = DataSize.ofMegabytes(10);

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

    public DataSize maxRolloverSize()
    {
        return maxRolloverSize;
    }
}
