package com.ethlo.http.processors.auth;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Primary
@Component
public class DelegateAuthorizationExtractor implements AuthorizationExtractor
{
    private final List<AuthorizationExtractor> providers;

    public DelegateAuthorizationExtractor(final List<AuthorizationExtractor> providers)
    {
        this.providers = providers;
    }

    @Override
    public Optional<RealmUser> getUser(HttpHeaders headers)
    {
        return providers.stream().map(p -> p.getUser(headers).orElse(null)).filter(Objects::nonNull).findFirst();
    }
}
