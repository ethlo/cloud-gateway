package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HandleDifferentResponseTypesTest extends BaseTest
{
    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void additionalConfigureProperties(DynamicPropertyRegistry registry)
    {
        // 1. ID & URI
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "for-junit-test");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://localhost:" + server.getPort());

        // 2. Predicates
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0].name", () -> "Path");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0].args.pattern", () -> "/get");

        // 3. Filters: InjectBasicAuth (Index 0)
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].name", () -> "InjectBasicAuth");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.username", () -> "foo");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.password", () -> "bar");

        // 4. Filters: SetRequestHeader (Index 1)
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].name", () -> "SetRequestHeader");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].args.name", () -> "x-realm");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].args.value", () -> "baz");

        // Point the gateway route to the dynamic WireMock port
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> "http://localhost:" + server.getPort()
        );

        configureClickHouseProperties(registry);
    }

    @Test
    void testChunkedGet()
    {
        // We simulate a chunked response by providing the body and NOT setting content-length.
        // WireMock handles the chunking mechanics.
        // If you want to force distinct chunks for testing timing, use withChunkedDribbleDelay.
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        // This forces WireMock to send Transfer-Encoding: chunked
                        // and break the body into pieces over time (5 chunks, 1000ms total)
                        .withChunkedDribbleDelay(5, 1000)
                        .withBody("Mozilla Developer Network")));

        final String body = client.get()
                .uri("/get")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // The WebClient (Netty) automatically decodes the chunks,
        // so we assert on the final re-assembled string.
        assertThat(body).isEqualTo("Mozilla Developer Network");
    }

    @Test
    void testSlowResponse()
    {
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withFixedDelay(3_000)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("Mozilla Developer Network")));

        final String body = client.get()
                .uri("/get")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isEqualTo("Mozilla Developer Network");
    }
}