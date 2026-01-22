package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ethlo.http.logger.delegate.SequentialDelegateLogger;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // FORCE THESE ON EARLY (Critical for @ConditionalOnProperty)
        "http-logging.auth.jwt.enabled=true",
        "http-logging.auth.basic.enabled=true"})
class JwtIntegrationTest extends BaseTest
{
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();
    @Autowired
    private WebTestClient webClient;

    @MockitoSpyBean
    private SequentialDelegateLogger httpLogger;

    @DynamicPropertySource
    static void additionalConfigureProperties(DynamicPropertyRegistry registry)
    {
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> "http://localhost:" + wireMock.getPort()
        );
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id",
                () -> "test-route"
        );
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/test/**"
        );

        // JWT auth extract
        registry.add("http-logging.auth.jwt.enabled", () -> "true");
        // CHANGE THIS: 'user-claim' -> 'username-claim-name' (to match getUsernameClaimName())
        registry.add("http-logging.auth.jwt.username-claim-name", () -> "preferred_username");

        // CHANGE THIS: Ensure this matches getRealmClaimName()
        registry.add("http-logging.auth.jwt.realm-claim-name", () -> "iss");

        // CHANGE THIS: Ensure this matches getRealmExpression()
        registry.add("http-logging.auth.jwt.realm-expression", () -> "([^/]+)/?$");

        // Basic Auth extract
        registry.add("http-logging.auth.basic.enabled", () -> "true");
        registry.add("http-logging.auth.basic.realm-header-name", () -> "x-realm");

        // Capture everything matching Path=/**"
        registry.add("http-logging.matchers[0].id", () -> "capture-all");
        registry.add("http-logging.matchers[0].predicates[0]", () -> "Path=/**");

        // Tell it WHAT to capture
        registry.add("http-logging.matchers[0].request.raw", () -> "STORE");
        registry.add("http-logging.matchers[0].request.body", () -> "STORE");
        registry.add("http-logging.matchers[0].response.raw", () -> "STORE");
        registry.add("http-logging.matchers[0].response.body", () -> "STORE");

        registry.add("http-logging.providers.file.enabled", () -> "true");
        registry.add("http-logging.providers.file.pattern", () -> "test-log-pattern");

        configureClickHouseProperties(registry);
    }

    @Test
    void shouldExtractUserFromJwtAndLogIt()
    {
        // Setup Upstream (WireMock) to return 200 OK
        wireMock.stubFor(get(urlEqualTo("/test/resource"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("OK")));

        // Generate a valid testing JWT
        String token = JWT.create()
                .withSubject("test-user-123") // The 'sub' claim
                .withIssuer("my-test-realm")  // The 'iss' claim
                .withClaim("preferred_username", "test-user-123")
                .withExpiresAt(Instant.now().plusSeconds(300))
                .sign(Algorithm.HMAC256("secret"));

        webClient.get()
                .uri("/test/resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        // We use a timeout because logging happens asynchronously in your filter (ioScheduler)
        verify(httpLogger, timeout(1000)).accessLog(argThat(data ->
        {
            // Check if the extractor actually ran and populated the user
            if (data.getUser().isEmpty())
            {
                return false;
            }

            return data.getUser().get().username().equals("test-user-123")
                    && data.getUser().get().realm().equals("my-test-realm");
        }));
    }

    @Test
    void shouldExtractUserEvenIfUpstreamReturns405()
    {
        // A. Setup Upstream to return 405 Method Not Allowed
        wireMock.stubFor(post(urlEqualTo("/test/resource"))
                .willReturn(aResponse()
                        .withStatus(405)));

        String token = JWT.create()
                .withSubject("test-user-405")
                .withIssuer("my-test-realm")
                .withClaim("preferred_username", "test-user-123")
                .sign(Algorithm.HMAC256("secret"));

        // C. Fire a POST request (which triggers the 405)
        webClient.post()
                .uri("/test/resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(405);

        // D. Verify extraction still happened
        verify(httpLogger, timeout(1000)).accessLog(argThat(data ->
        {
            if (data.getUser().isEmpty())
            {
                return false;
            }
            return data.getUser().get().username().equals("test-user-123");
        }));
    }
}