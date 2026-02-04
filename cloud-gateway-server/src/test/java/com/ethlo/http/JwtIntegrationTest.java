package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestTemplate;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ethlo.http.logger.delegate.DelegateHttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.processors.auth.RealmUser;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "http-logging.auth.jwt.enabled=true",
        "http-logging.auth.basic.enabled=true"})
class JwtIntegrationTest extends BaseTest
{
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @LocalServerPort
    private int gatewayPort;

    private RestTemplate restTemplate;

    @MockitoSpyBean
    private DelegateHttpLogger httpLogger;

    @DynamicPropertySource
    static void additionalConfigureProperties(DynamicPropertyRegistry registry)
    {
        final String wiremockUrl = "http://localhost:" + wireMock.getPort();
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].uri", () -> wiremockUrl);
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "test-route");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].predicates[0].name", () -> "Path");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].predicates[0].args.pattern", () -> "/test/**");

        registry.add("http-logging.auth.jwt.enabled", () -> "true");
        registry.add("http-logging.auth.jwt.username-claim-name", () -> "preferred_username");
        registry.add("http-logging.auth.jwt.realm-claim-name", () -> "iss");
        registry.add("http-logging.auth.jwt.realm-expression", () -> "([^/]+)/?$");
        registry.add("http-logging.auth.basic.enabled", () -> "true");
        registry.add("http-logging.auth.basic.realm-header-name", () -> "x-realm");

        configureClickHouseProperties(registry);
    }

    @BeforeEach
    void setUp()
    {
        this.restTemplate = new RestTemplate();
        // Uses NoOpResponseErrorHandler so we don't need the internal class boilerplate
        this.restTemplate.setErrorHandler(new org.springframework.web.client.NoOpResponseErrorHandler());
    }

    @Test
    void shouldExtractUserFromJwtAndLogIt()
    {
        wireMock.stubFor(get(urlEqualTo("/test/resource")).willReturn(aResponse().withStatus(200).withBody("OK")));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(createToken("test-user-123", "my-test-realm"));

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + gatewayPort + "/test/resource",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Capture the data to assert on it cleanly with AssertJ
        ArgumentCaptor<WebExchangeDataProvider> captor = ArgumentCaptor.forClass(WebExchangeDataProvider.class);
        verify(httpLogger).accessLog(any(), captor.capture());

        RealmUser user = captor.getValue().getUser().orElseThrow();
        assertThat(user.username()).isEqualTo("test-user-123");
        assertThat(user.realm()).isEqualTo("my-test-realm");
    }

    @Test
    void shouldExtractUserEvenIfUpstreamReturns405()
    {
        wireMock.stubFor(post(urlEqualTo("/test/resource")).willReturn(aResponse().withStatus(405)));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(createToken("test-user-123", "my-test-realm"));

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + gatewayPort + "/test/resource",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ArgumentCaptor<WebExchangeDataProvider> captor = ArgumentCaptor.forClass(WebExchangeDataProvider.class);
        verify(httpLogger).accessLog(any(), captor.capture());
        assertThat(captor.getValue().getUser().get().username()).isEqualTo("test-user-123");
    }

    private String createToken(String user, String issuer)
    {
        return JWT.create()
                .withSubject(user)
                .withIssuer(issuer)
                .withClaim("preferred_username", user)
                .withExpiresAt(Instant.now().plusSeconds(300))
                .sign(Algorithm.HMAC256("secret"));
    }
}