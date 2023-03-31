package com.ethlo.http.ratelimiter;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.processors.auth.extractors.AuthorizationExtractor;
import reactor.core.publisher.Mono;

@Component
public class UserCredentialsKeyResolver implements KeyResolver
{
    private final AuthorizationExtractor authorizationExtractor;

    public UserCredentialsKeyResolver(final AuthorizationExtractor authorizationExtractor)
    {
        this.authorizationExtractor = authorizationExtractor;
    }

    @Override
    public Mono<String> resolve(final ServerWebExchange exchange)
    {
        return Mono
                .fromCallable(() -> authorizationExtractor.getUser(exchange.getRequest().getHeaders(), exchange.getResponse().getHeaders())
                        .map(realmUser -> realmUser.realm() + " - " + realmUser.username()).orElse(null));
    }
}
