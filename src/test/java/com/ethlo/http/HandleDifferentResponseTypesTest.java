package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

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
    public void testChunkedGet() throws IOException
    {
        final int iterations = 10;
        try (final MockWebServer server = new MockWebServer())
        {
            for (int i = 0; i < iterations; i++)
            {
                server.enqueue(new MockResponse()
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .addHeader(HttpHeaders.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
                        .setBody("8\r\n" +
                                "Mozilla \r\n" +
                                "11\r\n" +
                                "Developer Network\r\n" +
                                "0\r\n" +
                                "\r\n"));
            }

            server.start(port);
            final HttpUrl url = server.url("/get");

            for (int i = 0; i < iterations; i++)
            {
                final String body = client.get()
                        .uri(url.uri().getPath())
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();

                assertThat(body).isEqualTo("Mozilla Developer Network");
            }
        }
    }
}