package com.ethlo.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class HandleDifferentResponseTypesTest
{
    @Autowired
    private WebTestClient client;

    @Test
    public void testSimpleGet() throws IOException
    {
        MockWebServer server = new MockWebServer();

        // Schedule some responses.
        server.enqueue(new MockResponse()
                .setHeaders(Headers.of(
                        "Content-Type", "application/json",
                        "Transfer-Encoding", "chunked",
                        "Date", "Fri, 17 Nov 2023 10:14:37 GMT"
                ))
                .setBody(new Buffer().write(("8\r\n" +
                        "Mozilla \r\n" +
                        "11\r\n" +
                        "Developer Network\r\n" +
                        "0\r\n" +
                        "\r\n").getBytes(StandardCharsets.UTF_8))));

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        final HttpUrl url = server.url("/get");

        final WebTestClient.BodyContentSpec body = client.get()
                .uri(url.uri())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();


        System.out.println(new String(body.returnResult().getResponseBody()));

        server.shutdown();
    }

    @Test
    public void testRedirect()
    {
        client.get()
                .uri("/redirect")
                .exchange()
                .expectStatus()
                .is3xxRedirection();
    }

    @TestConfiguration
    public static class TestRoutesConfiguration
    {
    }
}