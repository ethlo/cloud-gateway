package com.ethlo.http.filters.jwt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.springframework.scheduling.support.NoOpTaskScheduler;

import reactor.test.StepVerifier;

@WireMockTest
public class InjectAccessTokenAuthGatewayFilterFactoryTest
{
    final String refreshToken = JWT.create()
            .withIssuer("test-issuer")
            .withExpiresAt(OffsetDateTime.now().plusDays(7).toInstant())
            .sign(Algorithm.none());
    final String accessToken = JWT.create()
            .withIssuer("test-issuer")
            .withExpiresAt(Instant.now().plusSeconds(300))
            .sign(Algorithm.none()); // Replace with a valid algorithm for production
    private final String TOKEN_PATH = "/token";

    private final TaskScheduler taskScheduler = new NoOpTaskScheduler();
    private InjectAccessTokenAuthGatewayFilterFactory filterFactory;

    @BeforeEach
    public void setup()
    {
        filterFactory = new InjectAccessTokenAuthGatewayFilterFactory(taskScheduler);
    }

    @Test
    void testFetchAccessTokenWithBasicAuth(WireMockRuntimeInfo wmRuntimeInfo)
    {
        // Configure WireMock to respond for confidential client
        final String clientId = "confidential-client";
        final String clientSecret = "confidential-secret";
        final String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        stubFor(post(urlEqualTo(TOKEN_PATH))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(authorizationHeader))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"access_token\": \"" + accessToken + "\"}")
                )
        );

        final InjectAccessTokenConfig config = new InjectAccessTokenConfig()
                .setMinimumTTL(Duration.ofSeconds(30))
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .setTokenUrl(wmRuntimeInfo.getHttpBaseUrl() + TOKEN_PATH);

        StepVerifier.create(filterFactory.apply(config).fetchAccessToken())
                .expectNextMatches(jwt -> jwt.getToken().equals(accessToken))
                .verifyComplete();
    }

    @Test
    void testFetchAccessTokenForPublicClient(WireMockRuntimeInfo wmRuntimeInfo)
    {
        final String clientId = "public-client";

        stubFor(post(urlEqualTo(TOKEN_PATH))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .withRequestBody(containing("client_id=" + clientId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"access_token\": \"" + accessToken + "\"}")
                )
        );

        final InjectAccessTokenConfig config = new InjectAccessTokenConfig()
                .setClientId(clientId)
                .setRefreshToken(refreshToken)
                .setTokenUrl(wmRuntimeInfo.getHttpBaseUrl() + TOKEN_PATH);

        StepVerifier.create(filterFactory.apply(config).fetchAccessToken())
                .expectNextMatches(jwt -> jwt.getToken().equals(accessToken))
                .verifyComplete();
    }

    @Test
    void testFetchAccessTokenWithInvalidRefreshToken(WireMockRuntimeInfo wmRuntimeInfo)
    {
        // Configure WireMock to simulate invalid refresh token error
        String clientId = "public-client";
        String refreshToken = "invalid-refresh-token";

        stubFor(post(urlEqualTo(TOKEN_PATH))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .withRequestBody(containing("client_id=" + clientId))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {"error": "invalid_grant", "error_description": "Invalid refresh token"}""")
                )
        );

        // Create config for public client with invalid refresh token
        final InjectAccessTokenConfig config = new InjectAccessTokenConfig()
                .setClientId(clientId)
                .setRefreshToken(refreshToken)
                .setTokenUrl(wmRuntimeInfo.getHttpBaseUrl() + TOKEN_PATH);

        StepVerifier.create(filterFactory.apply(config).fetchAccessToken())
                .expectErrorMatches(error -> error instanceof TokenFetchException &&
                        error.getMessage().contains("Invalid refresh token"))
                .verify();
    }
}
