package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.netty.handler.codec.http.HttpHeaderValues;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class HandleDifferentResponseTypesTest
{
    // NOTE: Needs to match the config in test/resources/application.yaml
    private static final int port = 11117;

    @Autowired
    private WebTestClient client;

    @Test
    public void testChunkedGet() throws IOException, InterruptedException
    {
        try (final MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse()
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .addHeader(HttpHeaders.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
                    .setBody("""
                            8\r
                            Mozilla \r
                            11\r
                            Developer Network\r
                            0\r
                            \r
                            """));

            server.start(port);
            final HttpUrl url = server.url("/get");

            final String body = client.get()
                    .uri(url.uri().getPath())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body).isEqualTo("Mozilla Developer Network");
            Thread.sleep(100);
        }
    }

    @Test
    void testSlowResponse() throws IOException, InterruptedException
    {
        try (final MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse()
                    .setBodyDelay(3, TimeUnit.SECONDS)
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("Mozilla Developer Network"));
            server.start(port);
            final HttpUrl url = server.url("/get");

            final String body = client.get()
                    .uri(url.uri().getPath())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body).isEqualTo("Mozilla Developer Network");
            Thread.sleep(100);
        }
    }
}