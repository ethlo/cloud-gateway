package com.ethlo.http.processors.auth;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
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
        this.providers.sort(Comparator.comparingInt(Ordered::getOrder));
    }

    @Override
    public Optional<RealmUser> getUser(HttpHeaders headers)
    {
        for (AuthorizationExtractor e : providers)
        {
            final Optional<RealmUser> result = e.getUser(headers);
            if (result.isPresent())
            {
                return result;
            }
        }
        return Optional.empty();
    }

    @Override
    public int getOrder()
    {
        return 0;
    }
}
