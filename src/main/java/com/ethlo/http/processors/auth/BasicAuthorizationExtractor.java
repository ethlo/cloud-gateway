package com.ethlo.http.processors.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class BasicAuthorizationExtractor implements AuthorizationExtractor
{
    private final String realmHeaderParam;

    public BasicAuthorizationExtractor(@Value("${http-logging.auth.basic.realm-header-name}") final String realmHeaderParam)
    {
        this.realmHeaderParam = realmHeaderParam;
    }

    @Override
    public Optional<RealmUser> getUser(final HttpHeaders headers)
    {
        return Optional.ofNullable(headers.getFirst(HttpHeaders.AUTHORIZATION))
                .filter(headerValue -> headerValue.toLowerCase().startsWith("basic "))
                .flatMap(headerValue ->
                        decode(headerValue.substring(6)).map(usernameAndPassword ->
                        {
                            final String[] parts = usernameAndPassword.split(":");
                            if (parts.length == 2)
                            {
                                final String realm = headers.getFirst(realmHeaderParam);
                                return new RealmUser(realm, parts[0]);
                            }
                            return null;
                        }));
    }

    private Optional<String> decode(String data)
    {
        if (data == null)
        {
            return Optional.empty();
        }

        try
        {
            return Optional.of(new String(Base64.getDecoder().decode(data.trim()), StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException exc)
        {
            return Optional.empty();
        }
    }

    @Override
    public int getOrder()
    {
        return 1;
    }
}
