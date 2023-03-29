package com.ethlo.http.processors.auth;

import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;

public interface AuthorizationExtractor extends Ordered
{
    Optional<RealmUser> getUser(HttpHeaders headers);
}
