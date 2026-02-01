package com.ethlo.http.blocking.filters.ratelimiter;

import java.util.function.BiFunction;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import com.ethlo.http.blocking.ServletUtil;
import com.ethlo.http.processors.auth.extractors.AuthorizationExtractor;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UserCredentialsKeyResolver implements BiFunction<ServerRequest, HttpServletResponse, String>
{
    private final AuthorizationExtractor authorizationExtractor;

    public UserCredentialsKeyResolver(final AuthorizationExtractor authorizationExtractor)
    {
        this.authorizationExtractor = authorizationExtractor;
    }

    @Override
    public String apply(final ServerRequest serverRequest, final HttpServletResponse httpServletResponse)
    {
        return authorizationExtractor.getUser(ServletUtil.extractHeaders(serverRequest.servletRequest()), ServletUtil.extractHeaders(httpServletResponse))
                .map(realmUser -> realmUser.realm() + " - " + realmUser.username()).orElse(null);
    }
}
