package com.ethlo.http.blocking.filters.jwt;

import java.time.Duration;
import java.util.Optional;

import jakarta.validation.constraints.NotEmpty;

public class InjectAccessTokenConfig
{
    private static final Duration DEFAULT_MINIMUM_TTL = Duration.ofMinutes(1);
    @NotEmpty
    private String tokenUrl;

    @NotEmpty
    private String clientId;

    @NotEmpty
    private String refreshToken;

    private Duration minimumTTL;

    private String clientSecret;

    public String getTokenUrl()
    {
        return tokenUrl;
    }

    public InjectAccessTokenConfig setTokenUrl(final String tokenUrl)
    {
        this.tokenUrl = tokenUrl;
        return this;
    }

    public Duration getMinimumTTL()
    {
        return Optional.ofNullable(minimumTTL).orElse(DEFAULT_MINIMUM_TTL);
    }

    public InjectAccessTokenConfig setMinimumTTL(final Duration minimumTTL)
    {
        this.minimumTTL = Optional.ofNullable(minimumTTL).orElse(DEFAULT_MINIMUM_TTL);
        return this;
    }

    public String getClientSecret()
    {
        return clientSecret;
    }

    public InjectAccessTokenConfig setClientSecret(final String clientSecret)
    {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getClientId()
    {
        return clientId;
    }

    public InjectAccessTokenConfig setClientId(final String clientId)
    {
        this.clientId = clientId;
        return this;
    }

    public String getRefreshToken()
    {
        return refreshToken;
    }

    public InjectAccessTokenConfig setRefreshToken(final String refreshToken)
    {
        this.refreshToken = refreshToken;
        return this;
    }
}
