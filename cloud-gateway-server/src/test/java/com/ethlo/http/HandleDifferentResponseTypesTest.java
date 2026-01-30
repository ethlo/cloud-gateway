package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import com.ethlo.http.logger.delegate.SequentialDelegateLogger;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import reactor.core.publisher.Flux;

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
        // Adding the http-logging configuration
        registry.add("http-logging.auth.basic.enabled", () -> "true");
        registry.add("http-logging.auth.basic.realm-header-name", () -> "x-realm");

        // 3. Filters: InjectBasicAuth (Index 0)
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].name", () -> "InjectBasicAuth");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.username", () -> "foo");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.password", () -> "bar");

        // 4. Filters: SetRequestHeader (Index 1)
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].name", () -> "SetRequestHeader");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].args.name", () -> "x-realm");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].args.value", () -> "baz");

        registry.add("spring.cloud.gateway.server.webflux.routes[1].id", () -> "post-route");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].uri", () -> "http://localhost:" + server.getPort());
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[0].name", () -> "Path");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[0].args.pattern", () -> "/post");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[1].name", () -> "Method");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[1].args.methods", () -> "POST");

        registry.add("content-logging.buffer-files.cleanup", () -> "false");

        registry.add("spring.cloud.gateway.http-client.response-timeout", () -> "5s");

        // Point the gateway route to the dynamic WireMock port
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> "http://localhost:" + server.getPort()
        );

        configureClickHouseProperties(registry);
    }

    @BeforeEach
    void setUp()
    {
        this.client = client.mutate()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(2 * 1024 * 1024)) // Set to 2MB
                .build();
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

        finalResult.cleanup();
    }

    private byte[] gzipCompress(byte[] data) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos))
        {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    @Test
    void testLargePayloadLogging() throws IOException
    {
        // 1. Setup the observer for the background logging task
        final AtomicReference<AccessLogResult> logResultCapture = new AtomicReference<>();
        sequentialDelegateLogger.getResults()
                .subscribe(logResultCapture::set);

        // Generate 1MB of data for request and response
        final byte[] largeData = new byte[1024 * 1024];
        Arrays.fill(largeData, (byte) 'a');

        // 2. Mock WireMock with a large response body
        server.stubFor(post(urlPathEqualTo("/post"))
                .withRequestBody(binaryEqualTo(largeData))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        // This forces the 'Transfer-Encoding: chunked' header
                        .withChunkedDribbleDelay(5, 500)
                        .withBody(largeData)));

        // 3. Execute the POST request with large body
        byte[] compressedRequestData = gzipCompress(largeData);

        client.post()
                .uri("/post")
                // 1. Force Chunking: Pass as a Flux instead of bodyValue
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(compressedRequestData)), DataBuffer.class)
                // 2. Add headers so the Gateway knows it's Gzipped
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class).isEqualTo(largeData);

        // 4. SIGNAL THE TEST: Wait for the background task to finish
        // We increase the timeout slightly to account for disk I/O of 2MB total
        await().atMost(Duration.ofSeconds(10)).until(() -> logResultCapture.get() != null);

        AccessLogResult finalResult = logResultCapture.get();
        assertThat(finalResult.isOk()).isTrue();

        // 5. Verify the captured data integrity in the provider
        final WebExchangeDataProvider provider = finalResult.getWebExchangeDataProvider();

        // Check Request Body
        final byte[] capturedRequest = provider.getRequestBody()
                .map(BodyProvider::getInputStream)
                .map(buf ->
                {
                    try
                    {
                        return buf.readAllBytes();
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }).orElse(new byte[0]);
        assertThat(capturedRequest)
                .hasSameSizeAs(largeData)
                .containsExactly(largeData);

        // Check Response Body
        final byte[] capturedResponse = provider.getResponseBody()
                .map(BodyProvider::getInputStream)
                .map(buf ->
                {
                    try
                    {
                        return buf.readAllBytes();
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }).orElse(new byte[0]);
        assertThat(capturedResponse)
                .hasSameSizeAs(largeData)
                .containsExactly(largeData);

        finalResult.cleanup();
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

        assertThat(finalResult.getWebExchangeDataProvider().getDuration()).isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));

        finalResult.cleanup();
    }

    @Test
    void testUpstreamDown() throws IOException
    {
        // 1. Setup the observer for the background logging task
        final AtomicReference<AccessLogResult> logResultCapture = new AtomicReference<>();
        sequentialDelegateLogger.getResults()
                .subscribe(logResultCapture::set);

        // 2. Mock a "Connection Reset" or "Connection Refused" behavior
        server.stubFor(post(urlPathEqualTo("/post"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // 3. Execute the request
        final byte[] largeData = new byte[10 * 1024 * 1024];
        new Random().nextBytes(largeData);
        byte[] compressedRequestData = gzipCompress(largeData);

        client.post()
                .uri("/post")
                // Use a Flux and specify the element class to signal a streaming body
                .body(Flux.defer(() -> Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(compressedRequestData))), DataBuffer.class)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                // Optional: Explicitly ensure Content-Length is not present if your client bean has defaults
                .headers(headers -> headers.remove(HttpHeaders.CONTENT_LENGTH))
                .exchange()
                .expectStatus().isEqualTo(502);

        // 4. WAIT for the background logging to complete.
        // In a "down" scenario, the logging is triggered by the error signal
        // or the circuit breaker fallback.
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> logResultCapture.get() != null);

        // 5. Assertions
        final AccessLogResult finalResult = logResultCapture.get();
        assertThat(finalResult.isOk()).isTrue();

        // 6. Verify the metadata captured for the failed request
        final WebExchangeDataProvider provider = finalResult.getWebExchangeDataProvider();

        assertThat(provider.getStatusCode().value()).isEqualTo(502);

        // Verify the REQUEST body was captured despite the upstream failure
        assertThat(provider.getRequestBody()).isPresent();

        // verify the size matches the 'largeData'
        assertThat(provider.getRequestBody().get().getInputStream().readAllBytes()).hasSameSizeAs(largeData);

        assertThat(provider.getException()).isPresent();
        assertThat(provider.getException().get()).isInstanceOf(ResponseStatusException.class);

        assertThat(provider.getUser()).isPresent();

        // Verify no response content was logged (since the peer reset)
        assertThat(provider.getResponseBody()).isEmpty();

        finalResult.cleanup();
    }
}