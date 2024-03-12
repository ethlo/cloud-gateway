package com.ethlo.http.processors.auth.extractors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.processors.FeatureToggle;

@Validated
@RefreshScope
@ConfigurationProperties(prefix = "http-logging.auth.response-header")
public class ResponseAuthorizationConfig extends FeatureToggle
{
    /**
     * The response header name that is used extracting which realm the user belongs to
     */
    private String realmHeader;

    /**
     * The response header name that is used for deciding which username the user has
     */
    private String usernameHeader;

    /**
     * Whether to strip the headers before returning the response downstream
     */
    private boolean stripHeaders;

    public String getRealmHeader()
    {
        return realmHeader;
    }

    public void setRealmHeader(final String realmHeader)
    {
        this.realmHeader = realmHeader;
    }

    public String getUsernameHeader()
    {
        return usernameHeader;
    }

    public void setUsernameHeader(final String usernameHeader)
    {
        this.usernameHeader = usernameHeader;
    }

    public boolean isStripHeaders()
    {
        return stripHeaders;
    }

    public void setStripHeaders(final boolean stripHeaders)
    {
        this.stripHeaders = stripHeaders;
    }
}
