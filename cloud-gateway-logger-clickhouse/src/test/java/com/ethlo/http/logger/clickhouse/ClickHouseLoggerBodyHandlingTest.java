package com.ethlo.http.logger.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.RequestPath;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.logger.LoggingFilterService;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;

/**
 * An upstream that closes the connection mid-response leaves a chunked body without its terminating chunk, which
 * cannot be decoded. Capturing that is only a problem for a route that actually asked for the decoded body.
 */
class ClickHouseLoggerBodyHandlingTest
{
    private static final String REQUEST_ID = "truncated-request-id";

    private static final String TRUNCATED_CHUNKED_RESPONSE = """
            HTTP/1.1 200 OK\r
            Content-Type: text/event-stream\r
            Transfer-Encoding: chunked\r
            \r
            19\r
            event: message\ndata: hi\n\r
            """;

    @TempDir
    private Path logDirectory;

    private DataBufferRepository dataBufferRepository;

    @BeforeEach
    void setUp() throws IOException
    {
        final CaptureConfiguration captureConfiguration = new CaptureConfiguration();
        captureConfiguration.setLogDirectory(logDirectory);
        dataBufferRepository = new DataBufferRepository(captureConfiguration);

        dataBufferRepository.write(ServerDirection.RESPONSE, REQUEST_ID,
                ByteBuffer.wrap(TRUNCATED_CHUNKED_RESPONSE.getBytes(StandardCharsets.UTF_8))).join();
    }

    @Test
    void truncatedResponseIsNotAnErrorWhenTheBodyIsNotLogged()
    {
        final Map<String, Object> params = new HashMap<>();
        final AccessLogResult result = accessLog(new LogOptions(null, LogOptions.ContentProcessing.STORE, LogOptions.ContentProcessing.NONE), params);

        assertThat(result.getProcessingErrors()).isEmpty();
        assertThat(result.isOk()).isTrue();

        // The raw bytes are still captured, they are simply never handed to the body decoder
        assertThat(params).containsEntry("response_body", null).containsEntry("response_body_size", null);
        assertThat((byte[]) params.get("response_raw")).isEqualTo(TRUNCATED_CHUNKED_RESPONSE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void truncatedResponseIsReportedWhenTheBodyIsLogged()
    {
        final Map<String, Object> params = new HashMap<>();
        final AccessLogResult result = accessLog(new LogOptions(null, LogOptions.ContentProcessing.NONE, LogOptions.ContentProcessing.STORE), params);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).singleElement()
                .satisfies(e -> assertThat(e.getMessage()).contains("chunk-size"));
    }

    private AccessLogResult accessLog(final LogOptions responseOptions, final Map<String, Object> captured)
    {
        final PredicateConfig predicateConfig = new PredicateConfig("test-matcher", AsyncPredicate.from(exchange -> true),
                new LogOptions(null, LogOptions.ContentProcessing.NONE, LogOptions.ContentProcessing.NONE), responseOptions);

        final LoggingFilterService loggingFilterService = new LoggingFilterService(new HttpLoggingConfiguration());

        final ClickHouseLogger logger = new ClickHouseLogger(loggingFilterService, null)
        {
            @Override
            protected void insertIntoDatabase(final Map<String, Object> params)
            {
                captured.putAll(params);
            }
        };

        return logger.accessLog(dataProvider(predicateConfig)).join();
    }

    private WebExchangeDataProvider dataProvider(final PredicateConfig predicateConfig)
    {
        return new WebExchangeDataProvider(dataBufferRepository, predicateConfig)
                .route(Route.async().id("test-route").uri(URI.create("http://upstream")).predicate(exchange -> true).build())
                .requestId(REQUEST_ID)
                .method(HttpMethod.POST)
                .path(RequestPath.parse(URI.create("/mcp"), null))
                .uri(URI.create("http://gateway/mcp"))
                .statusCode(HttpStatus.OK)
                .requestHeaders(new HttpHeaders())
                .responseHeaders(new HttpHeaders())
                .timestamp(OffsetDateTime.now())
                .duration(Duration.ofMillis(26))
                .remoteAddress(null);
    }
}
