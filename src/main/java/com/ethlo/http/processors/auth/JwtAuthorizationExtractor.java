package com.ethlo.http.processors.auth;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;

@Component
public class JwtAuthorizationExtractor implements AuthorizationExtractor
{
    private final String realmClaim;
    private final String userClaimName;

    public JwtAuthorizationExtractor(@Value("${http-logging.auth.jwt.realm-claim}") final String realmClaim,
                                     @Value("${http-logging.auth.jwt.user-claim}") final String userClaimName)
    {
        this.realmClaim = realmClaim;
        this.userClaimName = userClaimName;
    }

    @Override
    public Optional<RealmUser> getUser(HttpHeaders headers)
    {
        try
        {
            final Optional<String> headerValue = Optional.ofNullable(headers.getFirst(AUTHORIZATION));
            return headerValue.map(h ->
            {
                if (h.toLowerCase().startsWith("bearer "))
                {
                    final DecodedJWT decodedJWT = JWT.decode(h.substring(7));
                    return new RealmUser(decodedJWT.getClaim(realmClaim).asString(), decodedJWT.getClaim(userClaimName).asString());
                }
                return null;
            });
        }
        catch (JWTDecodeException e)
        {
            return Optional.empty();
        }
    }
}
