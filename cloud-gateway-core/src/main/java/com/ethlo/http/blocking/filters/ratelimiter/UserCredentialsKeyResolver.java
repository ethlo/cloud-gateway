package com.ethlo.http.blocking.filters.ratelimiter;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import com.ethlo.http.blocking.ServletUtil;
import com.ethlo.http.blocking.filters.RequestKeyResolver;
import com.ethlo.http.processors.auth.extractors.AuthorizationExtractor;

@Component
public class UserCredentialsKeyResolver implements RequestKeyResolver
{
    private final AuthorizationExtractor authorizationExtractor;

    public UserCredentialsKeyResolver(final AuthorizationExtractor authorizationExtractor)
    {
        this.authorizationExtractor = authorizationExtractor;
    }

    @Override
    public String apply(final ServerRequest serverRequest)
    {
        return authorizationExtractor.getUser(ServletUtil.extractHeaders(serverRequest.servletRequest()), new HttpHeaders())
                .map(realmUser -> realmUser.realm() + " - " + realmUser.username()).orElse(null);
    }
}
