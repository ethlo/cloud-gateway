package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.ethlo.http.blocking.model.BodyProvider;
import com.ethlo.http.blocking.model.WebExchangeDataProvider;
import com.ethlo.http.logger.delegate.SequentialDelegateLogger;
import com.ethlo.http.model.AccessLogResult;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HandleDifferentResponseTypesTest extends BaseTest implements Consumer<AccessLogResult>
{
    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @LocalServerPort
    private int gatewayPort;

    private RestTemplate restTemplate;

    @Autowired
    private SequentialDelegateLogger sequentialDelegateLogger;

    private AccessLogResult finalResult;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry)
    {
        final String wiremockBaseUrl = "http://localhost:" + server.getPort();

        // Route 0: GET route
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "for-junit-test");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].uri", () -> wiremockBaseUrl);
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].predicates[0].name", () -> "Path");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].predicates[0].args.pattern", () -> "/get");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[0].name", () -> "InjectBasicAuth");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[0].args.username", () -> "foo");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[0].args.password", () -> "bar");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[1].name", () -> "SetRequestHeader");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[1].args.name", () -> "x-realm");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[1].args.value", () -> "baz");

        // Route 1: POST route
        registry.add("spring.cloud.gateway.server.webmvc.routes[1].id", () -> "post-route");
        registry.add("spring.cloud.gateway.server.webmvc.routes[1].uri", () -> wiremockBaseUrl);
        registry.add("spring.cloud.gateway.server.webmvc.routes[1].predicates[0].name", () -> "Path");
        registry.add("spring.cloud.gateway.server.webmvc.routes[1].predicates[0].args.pattern", () -> "/post");
        registry.add("spring.cloud.gateway.server.webmvc.routes[1].predicates[1].name", () -> "Method");
        registry.add("spring.cloud.gateway.server.webmvc.routes[1].predicates[1].args.methods", () -> "POST");

        // Global Configuration
        registry.add("spring.cloud.gateway.http-client.response-timeout", () -> "5s");
        registry.add("capture.enabled", () -> "true");
        registry.add("capture.log-directory", () -> "/tmp/cloud-gateway/raw");
        registry.add("providers.clickhouse.enabled", () -> "true");
        registry.add("providers.clickhouse.url", () -> "jdbc:clickhouse://localhost:8123?database=default");
        registry.add("providers.clickhouse.username", () -> "default");
        registry.add("providers.clickhouse.password", () -> "default");
        registry.add("providers.clickhouse.connection-init_sql", () -> "SET async_insert=1,wait_for_async_insert=1");
        registry.add("providers.file.enabled", () -> "true");
        registry.add("providers.file.pattern", () -> "{{gateway_request_id}} {{realm_claim}} {{user_claim}} {{method}} {{path}} {{host}} {{request_headers[\"Content-Length\"][0]}} {{status}} {{user_agent}} {{duration | numberformat(\"#.###\") }}");
        registry.add("http-logging.auth.basic.enabled", () -> "true");
        registry.add("http-logging.auth.basic.realm-header-name", () -> "x-realm");
        registry.add("content-logging.buffer-files.cleanup", () -> "false");
    }

    @BeforeEach
    void setUp()
    {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.setErrorHandler(new DefaultResponseErrorHandler()
        {
            @Override
            public boolean hasError(final ClientHttpResponse response) throws IOException
            {
                return false;
            }
        });

        sequentialDelegateLogger.addListener(this);
    }

    @Test
    void testChunkedGetWithLoggingVerification()
    {
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withChunkedDribbleDelay(5, 500)
                        .withBody("Mozilla Developer Network")));

        ResponseEntity<String> response = restTemplate.getForEntity(getGatewayUri("/get"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalResult.isOk()).isTrue();
        finalResult.cleanup();
    }

    @Test
    void testLargePayloadLogging() throws IOException
    {
        final byte[] largeData = new byte[1024 * 1024];
        Arrays.fill(largeData, (byte) 'a');

        server.stubFor(post(urlPathEqualTo("/post"))
                .withRequestBody(binaryEqualTo(largeData))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .withChunkedDribbleDelay(5, 500)
                        .withBody(largeData)));

        byte[] compressedRequestData = gzipCompress(largeData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");

        HttpEntity<byte[]> request = new HttpEntity<>(compressedRequestData, headers);
        ResponseEntity<byte[]> response = restTemplate.postForEntity(getGatewayUri("/post"), request, byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(largeData);

        final WebExchangeDataProvider capture = finalResult.getWebExchangeDataProvider();
        assertThat(readBody(capture.getRequestBody())).containsExactly(largeData);
        assertThat(readBody(capture.getResponseBody())).containsExactly(largeData);

        finalResult.cleanup();
    }

    @Test
    void testSlowResponse()
    {
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withFixedDelay(3_000)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("Mozilla Developer Network")));

        ResponseEntity<String> response = restTemplate.getForEntity(getGatewayUri("/get"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalResult.getWebExchangeDataProvider().getDuration())
                .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(500));

        finalResult.cleanup();
    }

    @Test
    void testUpstreamDown() throws IOException
    {
        server.stubFor(post(urlPathEqualTo("/post"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        final byte[] largeData = new byte[10 * 1024 * 1024];
        new Random().nextBytes(largeData);
        byte[] compressedRequestData = gzipCompress(largeData);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setBasicAuth("admin", "admin");

        HttpEntity<byte[]> request = new HttpEntity<>(compressedRequestData, headers);
        ResponseEntity<Void> response = restTemplate.exchange(getGatewayUri("/post"), HttpMethod.POST, request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        final WebExchangeDataProvider capture = finalResult.getWebExchangeDataProvider();
        assertThat(capture.getStatusCode().value()).isEqualTo(502);
        assertThat(readBody(capture.getRequestBody())).hasSameSizeAs(largeData);

        finalResult.cleanup();
    }

    private String getGatewayUri(String path)
    {
        return "http://localhost:" + gatewayPort + path;
    }

    private byte[] readBody(java.util.Optional<BodyProvider> provider)
    {
        return provider.map(p -> {
            try (InputStream is = p.getInputStream())
            {
                return is.readAllBytes();
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }).orElse(new byte[0]);
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

    @Override
    public void accept(final AccessLogResult accessLogResult)
    {
        this.finalResult = accessLogResult;
    }
}