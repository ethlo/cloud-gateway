package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

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
    public void testSimpleGet() throws IOException
    {
        try (final MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Transfer-Encoding", "chunked")
                    .setBody("8\r\n" +
                            "Mozilla \r\n" +
                            "11\r\n" +
                            "Developer Network\r\n" +
                            "0\r\n" +
                            "\r\n"));

            // Start the server.
            server.start(port);

            // Ask the server for its URL. You'll need this to make HTTP requests.
            final HttpUrl url = server.url("/get");

            final EntityExchangeResult<String> body = client.get()
                    .uri(url.uri().getPath())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class).returnResult();

            final String bodyContent = body.getResponseBody();
            assertThat(bodyContent).isEqualTo("Mozilla Developer Network");
        }
    }
}