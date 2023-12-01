package com.ethlo.http.processors.auth.extractors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.processors.FeatureToggle;

/**
 * Capture authentication information from the JWT contained in 'Authorization: Bearer ey...' header
 */
@Validated
@RefreshScope
@ConfigurationProperties(prefix = "http-logging.auth.jwt")
public class JwtAuthorizationConfig extends FeatureToggle
{
    /**
     * The claim that holds the realm
     */
    private String realmClaimName;

    /**
     * The claim that holds the username
     */
    private String usernameClaimName;

    public String getRealmClaimName()
    {
        return realmClaimName;
    }

    public String getUsernameClaimName()
    {
        return usernameClaimName;
    }

    public void setRealmClaimName(final String realmClaimName)
    {
        this.realmClaimName = realmClaimName;
    }

    public void setUsernameClaimName(final String usernameClaimName)
    {
        this.usernameClaimName = usernameClaimName;
    }
}
