package com.ethlo.http.processors.auth.extractors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.processors.FeatureToggle;
import jakarta.validation.constraints.NotNull;

@Validated
@RefreshScope
@ConditionalOnProperty("http-logging.auth.basic.enabled")
@ConfigurationProperties(prefix = "http-logging.auth.basic")
public class BasicAuthorizationConfig extends FeatureToggle
{
    /**
     * The header name that is used for deciding which realm to authenticate against in a multi-tenant server
     */
    @NotNull
    private String realmHeaderName;

    public String getRealmHeaderName()
    {
        return realmHeaderName;
    }

    public void setRealmHeaderName(final String realmHeaderName)
    {
        this.realmHeaderName = realmHeaderName;
    }
}
