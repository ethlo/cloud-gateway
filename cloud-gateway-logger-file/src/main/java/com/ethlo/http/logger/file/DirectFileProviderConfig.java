package com.ethlo.http.logger.direct_async;

import java.nio.file.Path;
import java.util.Optional;

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

    private final DataSize maxRolloverSize;

    public DirectFileProviderConfig(final boolean enabled, final String pattern, final Path storageDirectory, final DataSize maxRolloverSize)
    {
        super(enabled);
        this.pattern = pattern;
        this.storageDirectory = storageDirectory;
        this.maxRolloverSize = maxRolloverSize;
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
        return Optional.ofNullable(maxRolloverSize).orElse(DataSize.ofMegabytes(10));
    }
}
