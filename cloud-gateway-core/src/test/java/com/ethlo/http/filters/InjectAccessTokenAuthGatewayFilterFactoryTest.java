package com.ethlo.http.filters;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ethlo.http.filters.InjectAccessTokenAuthGatewayFilterFactory.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@ExtendWith(WireMockExtension.class)
public class InjectAccessTokenAuthGatewayFilterFactoryTest
{
    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private InjectAccessTokenAuthGatewayFilterFactory filterFactory;

    @BeforeEach
    public void setup()
    {
        filterFactory = new InjectAccessTokenAuthGatewayFilterFactory(HttpClient.create(), new ObjectMapper());
    }

    @Test
    public void testApply_ShouldInjectAccessTokenHeader()
    {
        // Set up WireMock to mock the token endpoint
        final String mockToken = JWT.create()
                .withIssuer("test-issuer")
                .withExpiresAt(Instant.now().plusSeconds(3600))
                .sign(Algorithm.none()); // Replace with a valid algorithm for production

        wireMockServer
                .stubFor(post("/token")
                        .withHeader("Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("clientId:clientSecret".getBytes(StandardCharsets.UTF_8))))
                        .willReturn(ok(mockToken)));

        // Set up configuration
        Config config = new Config()
                .setTokenUrl(wireMockServer.url("/token"))
                .setClientId("clientId")
                .setClientSecret("clientSecret")
                .setRefreshToken("refreshToken")
                .setMinimumTTL(Duration.ofMinutes(5));

        // Mock exchange and chain
        final ServerHttpRequest mockRequest = mock(ServerHttpRequest.class);
        when(mockRequest.getHeaders()).thenReturn(new HttpHeaders());
        final ServerWebExchange mockExchange = mock(ServerWebExchange.class);
        when(mockExchange.getRequest()).thenReturn(mockRequest);

        final GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(mockExchange)).thenReturn(Mono.empty());

        final ServerHttpRequest.Builder requestBuilder = mock(ServerHttpRequest.Builder.class);

        // Mock request behavior
        when(mockExchange.getRequest()).thenReturn(mockRequest);
        when(mockRequest.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.headers(any())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mockRequest);

        // Apply filter
        InjectAccessTokenAuthGatewayFilterFactory.Config configSpy = spy(config);
        filterFactory.apply(configSpy).filter(mockExchange, mockChain).block();

        // Verify that the Authorization header was added
        verify(mockExchange.getRequest().mutate().headers(headers ->
                headers.set("Authorization", "Bearer " + mockToken)
        ));

        // Verify that the WireMock stub was called
        final LoggedRequest req = wireMockServer.findRequestsMatching(RequestPattern.ANYTHING).getRequests().getFirst();
        assertThat(req.getHeader("Authorization")).isEqualTo("Basic " + Base64.getEncoder().encodeToString("clientId:clientSecret".getBytes(StandardCharsets.UTF_8)));
        assertThat(req.getBodyAsString()).isEqualTo("grant_type=refresh_token&refresh_token=refreshToken");
    }
}
