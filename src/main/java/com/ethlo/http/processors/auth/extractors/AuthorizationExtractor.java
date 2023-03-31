package com.ethlo.http.processors.auth.extractors;

import java.util.Optional;

import com.ethlo.http.processors.auth.RealmUser;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;

public interface AuthorizationExtractor extends Ordered
{
    Optional<RealmUser> getUser(HttpHeaders headers, final HttpHeaders responseHeaders);
}
