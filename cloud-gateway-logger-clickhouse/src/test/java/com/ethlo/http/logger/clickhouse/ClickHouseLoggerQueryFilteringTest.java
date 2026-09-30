package com.ethlo.http.logger.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
import com.ethlo.http.logger.LogFilter;
import com.ethlo.http.logger.LoggingFilterService;
import com.ethlo.http.match.QueryParamPredicate;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;

/**
 * Unlike headers, query parameters are hidden by default because they frequently carry sensitive data
 * (API keys, tokens). They are only surfaced for names explicitly added to the "query-params" accept-list.
 */
class ClickHouseLoggerQueryFilteringTest
{
    @TempDir
    private Path logDirectory;

    @Test
    void queryIsOmittedByDefault() throws IOException
    {
        final Map<String, Object> params = accessLog(new HttpLoggingConfiguration(), "foo=bar&api_key=secret");
        assertThat(params).containsEntry("query", null);
    }

    @Test
    void onlyAcceptListedParamsAreLogged() throws IOException
    {
        final HttpLoggingConfiguration configuration = new HttpLoggingConfiguration();
        configuration.setFilter(new LogFilter().setQueryParams(new QueryParamPredicate(Set.of("foo"), null)));

        final Map<String, Object> params = accessLog(configuration, "foo=bar&api_key=secret");
        assertThat(params).containsEntry("query", "foo=bar");
    }

    private Map<String, Object> accessLog(final HttpLoggingConfiguration configuration, final String rawQuery) throws IOException
    {
        final CaptureConfiguration captureConfiguration = new CaptureConfiguration();
        captureConfiguration.setLogDirectory(logDirectory);
        final DataBufferRepository dataBufferRepository = new DataBufferRepository(captureConfiguration);

        final PredicateConfig predicateConfig = new PredicateConfig("test-matcher", AsyncPredicate.from(exchange -> true),
                new LogOptions(null, LogOptions.ContentProcessing.NONE, LogOptions.ContentProcessing.NONE),
                new LogOptions(null, LogOptions.ContentProcessing.NONE, LogOptions.ContentProcessing.NONE));

        final LoggingFilterService loggingFilterService = new LoggingFilterService(configuration);

        final Map<String, Object> captured = new HashMap<>();
        final ClickHouseLogger logger = new ClickHouseLogger(loggingFilterService, null)
        {
            @Override
            protected void insertIntoDatabase(final Map<String, Object> params)
            {
                captured.putAll(params);
            }
        };

        final WebExchangeDataProvider dataProvider = new WebExchangeDataProvider(dataBufferRepository, predicateConfig)
                .route(Route.async().id("test-route").uri(URI.create("http://upstream")).predicate(exchange -> true).build())
                .requestId("query-filter-test")
                .method(HttpMethod.GET)
                .path(RequestPath.parse(URI.create("/api"), null))
                .uri(URI.create("http://gateway/api?" + rawQuery))
                .statusCode(HttpStatus.OK)
                .requestHeaders(new HttpHeaders())
                .responseHeaders(new HttpHeaders())
                .timestamp(OffsetDateTime.now())
                .duration(Duration.ofMillis(5))
                .remoteAddress(null);

        logger.accessLog(dataProvider).join();
        return captured;
    }
}
