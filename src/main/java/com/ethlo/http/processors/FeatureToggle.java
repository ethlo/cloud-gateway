package com.ethlo.http.processors;

public abstract class FeatureToggle
{
    /**
     * Whether this feature is enabled
     */
    private final boolean enabled;

    protected FeatureToggle(final boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isEnabled()
    {
        return enabled;
    }
}
