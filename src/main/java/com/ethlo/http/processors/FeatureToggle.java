package com.ethlo.http.processors;

import java.util.Optional;

public abstract class FeatureToggle
{
    /**
     * Whether this feature is enabled. Defaults to true
     */
    private Boolean enabled;

    public boolean isEnabled()
    {
        return Optional.ofNullable(enabled).orElse(true);
    }

    public void setEnabled(final Boolean enabled)
    {
        this.enabled = enabled;
    }
}
