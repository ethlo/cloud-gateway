package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.processors.auth.RealmUser;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.JwtAuthorizationExtractor;

class JwtAuthorizationExtractorTest
{
    @Test
    void testJwt()
    {
        final String jwtString = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwtString);
        final JwtAuthorizationConfig config = new JwtAuthorizationConfig();
        config.setRealmClaimName("realm");
        config.setUsernameClaimName("sub");
        assertThat(new JwtAuthorizationExtractor(config).getUser(headers, null)).hasValue(new RealmUser(null, "1234567890"));
    }
}
