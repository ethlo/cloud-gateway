package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.processors.auth.RealmUser;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationExtractor;

class JwtAuthorizationExtractorTest
{
    private final String jwtString = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2xvZ2luLmV4YW1wbGUuY29tL3JlYWxtcy9hY21lIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTUxNjIzOTAyMn0.sIWKkE-9tq-0jDMuZA4H129-0OxJo9xvOW4jwsH33ao";

    @Test
    void testJwt()
    {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwtString);
        final JwtAuthorizationConfig config = new JwtAuthorizationConfig();
        config.setRealmClaimName("iss");
        config.setRealmExpression("([^/]+)/?$");
        config.setUsernameClaimName("sub");
        assertThat(new JwtAuthorizationExtractor(config).getUser(headers, null)).hasValue(new RealmUser("acme", "1234567890"));
    }
}
