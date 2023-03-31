package com.ethlo.http.processors.auth.extractors;

import com.ethlo.http.processors.FeatureToggle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Capture authentication information from the JWT contained in 'Authorization: Bearer ey...' header
 */
@ConfigurationProperties(prefix = "http-logging.auth.jwt")
public class JwtAuthorizationConfig extends FeatureToggle
{
    /**
     * The claim that holds the realm
     */
    private final String realmClaimName;

    /**
     * The claim that holds the username
     */
    private final String usernameClaimName;

    public JwtAuthorizationConfig(final boolean enabled, final String realmClaimName, final String usernameClaimName)
    {
        super(enabled);
        this.realmClaimName = realmClaimName;
        this.usernameClaimName = usernameClaimName;
    }

    public String getRealmClaimName()
    {
        return realmClaimName;
    }

    public String getUsernameClaimName()
    {
        return usernameClaimName;
    }
}
