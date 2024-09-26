package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.processors.auth.RealmUser;
import com.ethlo.http.processors.auth.extractors.BasicAuthorizationConfig;
import com.ethlo.http.processors.auth.extractors.BasicAuthorizationExtractor;

class BasicAuthorizationExtractorTest
{
    @Test
    void testBasic()
    {
        final String authString = Base64.getEncoder().encodeToString("myuser:mypassword".getBytes(StandardCharsets.UTF_8));
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "basic " + authString);
        headers.add("x-realm", "acme");
        final BasicAuthorizationConfig config = new BasicAuthorizationConfig();
        config.setEnabled(true);
        config.setRealmHeaderName("x-realm");
        assertThat(new BasicAuthorizationExtractor(config).getUser(headers, null).orElseThrow()).isEqualTo(new RealmUser("acme", "myuser"));
    }
}
