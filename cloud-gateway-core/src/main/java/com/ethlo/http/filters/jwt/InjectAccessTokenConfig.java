package com.ethlo.http.filters.jwt;

import jakarta.validation.constraints.NotEmpty;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

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

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof final InjectAccessTokenConfig other))
        {
            return false;
        }
        return Objects.equals(tokenUrl, other.tokenUrl)
                && Objects.equals(clientId, other.clientId)
                && Objects.equals(clientSecret, other.clientSecret)
                && Objects.equals(refreshToken, other.refreshToken)
                && Objects.equals(getMinimumTTL(), other.getMinimumTTL());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tokenUrl, clientId, clientSecret, refreshToken, getMinimumTTL());
    }
}
