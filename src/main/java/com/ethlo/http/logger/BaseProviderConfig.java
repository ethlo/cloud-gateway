package com.ethlo.http.logger;

public abstract class BaseProviderConfig
{
    private final boolean enabled;

    protected BaseProviderConfig(final boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isEnabled()
    {
        return enabled;
    }
}
