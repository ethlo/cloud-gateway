package com.ethlo.http.processors.auth.extractors;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.Optional;

import com.ethlo.http.processors.auth.RealmUser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpHeaders;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;

import org.springframework.stereotype.Component;

@RefreshScope
@Component
@ConditionalOnProperty("http-logging.auth.jwt.enabled")
public class JwtAuthorizationExtractor implements AuthorizationExtractor
{
    private final JwtAuthorizationConfig config;

    public JwtAuthorizationExtractor(JwtAuthorizationConfig config)
    {
        this.config = config;
    }

    @Override
    public Optional<RealmUser> getUser(HttpHeaders headers, final HttpHeaders responseHeaders)
    {
        if (! config.isEnabled())
        {
            return Optional.empty();
        }

        try
        {
            final Optional<String> headerValue = Optional.ofNullable(headers.getFirst(AUTHORIZATION));
            return headerValue.map(h ->
            {
                if (h.toLowerCase().startsWith("bearer "))
                {
                    final DecodedJWT decodedJWT = JWT.decode(h.substring(7));
                    return new RealmUser(decodedJWT.getClaim(config.getRealmClaimName()).asString(), decodedJWT.getClaim(config.getUsernameClaimName()).asString());
                }
                return null;
            });
        }
        catch (JWTDecodeException e)
        {
            return Optional.empty();
        }
    }

    @Override
    public int getOrder()
    {
        return 2;
    }
}
