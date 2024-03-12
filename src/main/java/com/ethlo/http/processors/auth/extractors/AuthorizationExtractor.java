package com.ethlo.http.processors.auth.extractors;

import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.processors.auth.RealmUser;

public interface AuthorizationExtractor extends Ordered
{
    Optional<RealmUser> getUser(HttpHeaders headers, final HttpHeaders responseHeaders);
}
