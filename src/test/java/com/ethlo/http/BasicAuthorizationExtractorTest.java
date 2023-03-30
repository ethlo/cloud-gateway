package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.ethlo.http.processors.BasicAuthorizationConfig;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.processors.auth.BasicAuthorizationExtractor;
import com.ethlo.http.processors.auth.RealmUser;

class BasicAuthorizationExtractorTest
{
    @Test
    void testBasic()
    {
        final String authString = Base64.getEncoder().encodeToString("myuser:mypassword".getBytes(StandardCharsets.UTF_8));
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "basic " + authString);
        headers.add("x-realm", "acme");
        assertThat(new BasicAuthorizationExtractor(new BasicAuthorizationConfig(true, "x-realm")).getUser(headers).orElseThrow()).isEqualTo(new RealmUser("acme", "myuser"));
    }
}
