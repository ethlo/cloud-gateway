package com.ethlo.http.processors;

import com.ethlo.http.processors.auth.extractors.BasicAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationConfig;

public class AuthorizationExtractorConfig
{
    /**
     * Configuration for basic auth extraction
     */
    private final BasicAuthorizationConfig basic;

    /**
     * Configuration for JWT auth extraction
     */
    private final JwtAuthorizationConfig jwt;

    public AuthorizationExtractorConfig(final BasicAuthorizationConfig basic, final JwtAuthorizationConfig jwt)
    {
        this.basic = basic;
        this.jwt = jwt;
    }

    public BasicAuthorizationConfig getBasic()
    {
        return basic;
    }

    public JwtAuthorizationConfig getJwt()
    {
        return jwt;
    }
}
