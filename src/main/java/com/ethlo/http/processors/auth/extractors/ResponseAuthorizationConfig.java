package com.ethlo.http.processors.auth.extractors;

import com.ethlo.http.processors.FeatureToggle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "http-logging.auth.response-header")
public class ResponseAuthorizationConfig extends FeatureToggle
{
    /**
     * The response header name that is used extracting which realm the user belongs to
     */
    private final String realmHeader;

    /**
     * The response header name that is used for deciding which username the user has
     */
    private final String usernameHeader;

    /**
     * Whether to strip the headers before returning the response downstream
     */
    private final boolean stripHeaders;

    public ResponseAuthorizationConfig(final boolean enabled, final String realmHeader, final String usernameHeader, final boolean stripHeaders)
    {
        super(enabled);
        this.realmHeader = realmHeader;
        this.usernameHeader = usernameHeader;
        this.stripHeaders = stripHeaders;
    }

    public String getRealmHeader()
    {
        return realmHeader;
    }

    public String getUsernameHeader()
    {
        return usernameHeader;
    }

    public boolean isStripHeaders()
    {
        return stripHeaders;
    }
}
