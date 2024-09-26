package com.ethlo.http.processors.auth.extractors;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.ethlo.http.processors.auth.RealmUser;

@Component
@RefreshScope
@ConditionalOnProperty("http-logging.auth.response-header.enabled")
public class ResponseAuthorizationExtractor implements AuthorizationExtractor
{
    private final ResponseAuthorizationConfig config;

    public ResponseAuthorizationExtractor(final ResponseAuthorizationConfig config)
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

        final String realm = responseHeaders.getFirst(config.getRealmHeader());
        final String username = responseHeaders.getFirst(config.getUsernameHeader());
        return Optional.ofNullable(username).map(u -> new RealmUser(realm, u));
    }

    @Override
    public int getOrder()
    {
        return 0;
    }
}
