package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.WireMockServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HandleDifferentResponseTypesTest
{
    // NOTE: Needs to match the config in test/resources/application.yaml
    private static final int port = 11117;
    private final WireMockServer server = new WireMockServer(port);
    @Autowired
    private WebTestClient client;

    @BeforeEach
    protected void setup()
    {
        server.start();
    }

    @AfterEach
    void teardown()
    {
        server.stop();
    }

    @Disabled("Investigate")
    @Test
    void testChunkedGet()
    {
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withHeader(HttpHeaders.TRANSFER_ENCODING, "chunked")
                        .withBody("""
                                8\r
                                Mozilla \r
                                11\r
                                Developer Network\r
                                0\r
                                \r
                                """)));

        final String body = client.get()
                .uri("/get")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

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
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isEqualTo("Mozilla Developer Network");
    }
}