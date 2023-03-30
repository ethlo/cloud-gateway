package com.ethlo.http.processors;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "http-logging.auth.basic")
public class BasicAuthorizationConfig extends FeatureToggle
{
    /**
     * The header name that is used for deciding which realm to authenticate against in a multi-tenant server
     */
    private final String realmHeaderName;

    public BasicAuthorizationConfig(final boolean enabled, final String realmHeaderName)
    {
        super(enabled);
        this.realmHeaderName = realmHeaderName;
    }

    public String getRealmHeaderName()
    {
        return realmHeaderName;
    }
}
