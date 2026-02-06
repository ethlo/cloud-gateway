package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ethlo.http.match.LogOptions;
import com.ethlo.http.match.RequestMatchingProcessor;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.util.ConfigUtil;

class LoggingPredicateReadTest
{
    @Test
    void readsMatcher_shouldMatchPlainGetWithoutExtension()
    {
        // Mimic YAML binder output
        final Map<String, Object> rawPredicates = new LinkedHashMap<>();
        rawPredicates.put("0",
                "NotHost=target-server.**,login.**,sso-admin-server.**,monitoring.**,admin.**,api-monitor-server.**");
        rawPredicates.put("1", "Method=GET");
        rawPredicates.put("2", "NotPath=/api/live/ws,/api/events");
        rawPredicates.put("3", "NotExtension=");

        final RequestMatchingProcessor processor =
                new RequestMatchingProcessor(
                        "reads",
                        rawPredicates,
                        null,
                        new LogOptions(null, null, null)
                );

        final PredicateConfig config =
                ConfigUtil.toMatchers(List.of(processor)).getFirst();

        // GET without extension, allowed host, allowed path
        MockHttpServletRequest request =
                createRequest("GET", "http://api.example.com/api/orders", "api.example.com");

        assertThat(config.predicate().test(request))
                .as("GET without extension should match reads matcher")
                .isTrue();
    }

    @Test
    void readsMatcher_shouldRejectGetWithExtension()
    {
        final Map<String, Object> rawPredicates = new LinkedHashMap<>();
        rawPredicates.put("0",
                "NotHost=target-server.**,login.**,sso-admin-server.**,monitoring.**,admin.**,api-monitor-server.**");
        rawPredicates.put("1", "Method=GET");
        rawPredicates.put("2", "NotPath=/api/live/ws,/api/events");
        rawPredicates.put("3", "NotExtension=");

        final RequestMatchingProcessor processor =
                new RequestMatchingProcessor("reads", rawPredicates, null, null);

        final PredicateConfig config =
                ConfigUtil.toMatchers(List.of(processor)).getFirst();

        // GET *with* extension → should fail NotExtension
        MockHttpServletRequest request =
                createRequest("GET", "http://api.example.com/api/orders.json", "api.example.com");

        assertThat(config.predicate().test(request))
                .as("GET with extension should be rejected by NotExtension")
                .isFalse();
    }

    @Test
    void readsMatcher_shouldRejectNonGet()
    {
        final Map<String, Object> rawPredicates = new LinkedHashMap<>();
        rawPredicates.put("0",
                "NotHost=target-server.**,login.**,sso-admin-server.**,monitoring.**,admin.**,api-monitor-server.**");
        rawPredicates.put("1", "Method=GET");
        rawPredicates.put("2", "NotPath=/api/live/ws,/api/events");
        rawPredicates.put("3", "NotExtension=");

        final RequestMatchingProcessor processor =
                new RequestMatchingProcessor("reads", rawPredicates, null, null);

        final PredicateConfig config =
                ConfigUtil.toMatchers(List.of(processor)).getFirst();

        MockHttpServletRequest request =
                createRequest("POST", "http://api.example.com/api/orders", "api.example.com");

        assertThat(config.predicate().test(request))
                .as("POST should not match Method=GET")
                .isFalse();
    }

    private static MockHttpServletRequest createRequest(String method, String url, String host)
    {
        URI uri = URI.create(url);
        MockHttpServletRequest request =
                new MockHttpServletRequest(method, uri.getPath());
        request.setServerName(uri.getHost());
        request.addHeader("Host", host);
        return request;
    }
}
