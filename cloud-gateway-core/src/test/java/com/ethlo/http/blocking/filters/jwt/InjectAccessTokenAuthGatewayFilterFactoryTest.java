/*package com.ethlo.http.blocking.filters.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.wiremock.client.WireMock.aResponse;
import static org.wiremock.client.WireMock.containing;
import static org.wiremock.client.WireMock.equalTo;
import static org.wiremock.client.WireMock.post;
import static org.wiremock.client.WireMock.urlEqualTo;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.NoOpTaskScheduler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ethlo.http.blocking.filters.AbstractFilterTest;
import org.wiremock.client.WireMock; // Corrected package for 3.x
import org.wiremock.junit5.WireMockRuntimeInfo;
import org.wiremock.junit5.WireMockTest;

@WireMockTest
public class InjectAccessTokenAuthFilterSupplierTest extends AbstractFilterTest<InjectAccessTokenConfig>
{
    private final String refreshToken = JWT.create()
            .withIssuer("test-issuer")
            .withExpiresAt(OffsetDateTime.now().plusDays(7).toInstant())
            .sign(Algorithm.none());

    private final String accessToken = JWT.create()
            .withIssuer("test-issuer")
            .withExpiresAt(Instant.now().plusSeconds(300))
            .sign(Algorithm.none());

    private final String TOKEN_PATH = "/token";
    private final TaskScheduler taskScheduler = new NoOpTaskScheduler();

    private InjectAccessTokenAuthFilterSupplier filterSupplier;

    @BeforeEach
    public void init()
    {
        filterSupplier = new InjectAccessTokenAuthFilterSupplier(taskScheduler);
    }

    @Override
    protected FilterSupplier filterSupplier()
    {
        return filterSupplier;
    }

    @Override
    protected String getFilterName()
    {
        return "injectAccessTokenAuth";
    }

    @Test
    void testFetchAccessTokenWithBasicAuth(WireMockRuntimeInfo wmRuntimeInfo)
    {
        // Use the instance-specific WireMock client
        final WireMock wm = wmRuntimeInfo.getWireMock();
        final String clientId = "confidential-client";
        final String clientSecret = "confidential-secret";
        final String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        wm.register(post(urlEqualTo(TOKEN_PATH))
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

        String fetchedToken = filterSupplier.fetchAccessToken(config).getToken();
        assertThat(fetchedToken).isEqualTo(accessToken);
    }

    @Test
    void testFetchAccessTokenWithInvalidRefreshToken(WireMockRuntimeInfo wmRuntimeInfo)
    {
        final WireMock wm = wmRuntimeInfo.getWireMock();
        final String clientId = "public-client";
        final String badRefreshToken = "invalid-refresh-token";

        wm.register(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"error\": \"invalid_grant\", \"error_description\": \"Invalid refresh token\"}")
                )
        );

        final InjectAccessTokenConfig config = new InjectAccessTokenConfig()
                .setClientId(clientId)
                .setRefreshToken(badRefreshToken)
                .setTokenUrl(wmRuntimeInfo.getHttpBaseUrl() + TOKEN_PATH);

        assertThatThrownBy(() -> filterSupplier.fetchAccessToken(config))
                .isInstanceOf(TokenFetchException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void testFilterInjectsHeader(WireMockRuntimeInfo wmRuntimeInfo) throws Exception
    {
        final WireMock wm = wmRuntimeInfo.getWireMock();
        wm.register(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"access_token\": \"" + accessToken + "\"}")
                )
        );

        final InjectAccessTokenConfig config = new InjectAccessTokenConfig()
                .setClientId("test-client")
                .setRefreshToken(refreshToken)
                .setTokenUrl(wmRuntimeInfo.getHttpBaseUrl() + TOKEN_PATH);

        // Execute via AbstractFilterTest logic
        execute(config);

        // Verify mutated request headers
        assertThat(actualRequest().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer " + accessToken);
    }
}
 */