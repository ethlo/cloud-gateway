package com.ethlo.http.processors.auth;

import java.util.Optional;

import org.springframework.http.HttpHeaders;

public interface AuthorizationExtractor
{
    Optional<RealmUser> getUser(HttpHeaders headers);
}
