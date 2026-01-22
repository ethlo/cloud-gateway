package com.ethlo.http.processors.auth.extractors;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.Optional;
import java.util.regex.Matcher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ethlo.http.processors.auth.RealmUser;

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
        if (!config.isEnabled())
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
                    final String realmClaimValue = decodedJWT.getClaim(config.getRealmClaimName()).asString();
                    final String realm = config.getRealmExpression().map(p ->
                    {
                        final Matcher matcher = p.matcher(realmClaimValue);
                        return matcher.find() ? matcher.group() : null;
                    }).orElse(realmClaimValue);

                    return new RealmUser(realm, decodedJWT.getClaim(config.getUsernameClaimName()).asString());
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
