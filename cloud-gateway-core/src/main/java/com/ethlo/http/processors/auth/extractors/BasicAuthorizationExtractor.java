package com.ethlo.http.processors.auth.extractors;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.processors.auth.RealmUser;
import jakarta.validation.constraints.NotNull;

@ConditionalOnProperty("http-logging.auth.basic.enabled")
@Validated
@Component
@RefreshScope
public class BasicAuthorizationExtractor implements AuthorizationExtractor
{
    private final BasicAuthorizationConfig config;

    public BasicAuthorizationExtractor(@NotNull BasicAuthorizationConfig config)
    {
        this.config = config;
    }

    @Override
    public Optional<RealmUser> getUser(final HttpHeaders requestHeaders, final HttpHeaders responseHeaders)
    {
        if (!config.isEnabled())
        {
            return Optional.empty();
        }

        return Optional.ofNullable(requestHeaders.getFirst(HttpHeaders.AUTHORIZATION))
                .filter(headerValue -> headerValue.toLowerCase().startsWith("basic "))
                .flatMap(headerValue ->
                        decode(headerValue.substring(6)).map(usernameAndPassword ->
                        {
                            final String[] parts = usernameAndPassword.split(":");
                            if (parts.length == 2)
                            {
                                final String realm = requestHeaders.getFirst(config.getRealmHeaderName());
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
