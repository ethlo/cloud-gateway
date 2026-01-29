package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.ethlo.http.logger.delegate.SequentialDelegateLogger;
import com.ethlo.http.model.AccessLogResult;
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

    @Autowired
    private SequentialDelegateLogger sequentialDelegateLogger;

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

        registry.add("spring.cloud.gateway.http-client.response-timeout", () -> "5s");

        // Point the gateway route to the dynamic WireMock port
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> "http://localhost:" + server.getPort()
        );

        configureClickHouseProperties(registry);
    }

    @Test
    void testChunkedGetWithLoggingVerification()
    {
        // 1. Setup an observer for the background logging task
        final AtomicReference<AccessLogResult> logResultCapture = new AtomicReference<>();
        sequentialDelegateLogger.getResults()
                .subscribe(logResultCapture::set);

        // 2. Mock WireMock
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withChunkedDribbleDelay(5, 500)
                        .withBody("Mozilla Developer Network")));

        // 3. Execute the request
        client.get().uri("/get").exchange().expectStatus().isOk();

        // 4. SIGNAL THE TEST: Wait for the background task to finish and check for failures
        await().atMost(Duration.ofSeconds(5)).until(() -> logResultCapture.get() != null);

        AccessLogResult finalResult = logResultCapture.get();

        assertThat(finalResult.isOk())
                .withFailMessage("Logging failed with errors: %s", finalResult.getProcessingErrors())
                .isTrue();
    }

    @Test
    void testSlowResponse()
    {
        // 1. Setup the observer for the background logging task
        final AtomicReference<AccessLogResult> logResultCapture = new AtomicReference<>();
        sequentialDelegateLogger.getResults()
                .subscribe(logResultCapture::set);

        // 2. Mock a slow response (3 seconds)
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withFixedDelay(3_000)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("Mozilla Developer Network")));

        // 3. Execute the request
        final String body = client.get()
                .uri("/get")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // 4. Verify the response content
        assertThat(body).isEqualTo("Mozilla Developer Network");

        // 5. WAIT for the background logging to complete.
        // Even though the response is slow, the logger should trigger as soon as
        // the Flux completes in the ResponsePayloadCaptureDecorator.
        await()
                .atMost(Duration.ofSeconds(10)) // 3s delay + safety margin
                .pollInterval(Duration.ofMillis(100))
                .until(() -> logResultCapture.get() != null);

        // 6. Assert that the background task actually succeeded
        final AccessLogResult finalResult = logResultCapture.get();
        assertThat(finalResult.isOk())
                .withFailMessage("Logging failed for slow response. Errors: %s",
                        finalResult.getProcessingErrors()
                )
                .isTrue();
    }
}