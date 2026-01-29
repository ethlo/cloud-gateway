package com.ethlo.http.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Valid
@Validated
public record UpstreamService(@NotNull String name, @NotNull URI configUrl) implements Comparable<UpstreamService>
{
    @Override
    public int compareTo(@NotNull final UpstreamService upstreamService)
    {
        return name.compareTo(upstreamService.name);
    }
}