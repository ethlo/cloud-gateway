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

class LoggingPredicateTest
{

    @Test
    void testMixedMapRepresentations()
    {
        // 1. Prepare the raw map mimic from YAML binder (as seen in your debugger)
        final Map<String, Object> rawPredicates = new LinkedHashMap<>();

        // Representation A: String Shortcut
        rawPredicates.put("0", "Path=/api/**");

        // Representation B: Structured Map (Double Nested)
        final Map<String, Object> headerOuter = new LinkedHashMap<>();
        final Map<String, String> headerInner = new LinkedHashMap<>();
        headerInner.put("name", "X-Request-Id");
        headerInner.put("regexp", "\\d+");
        headerOuter.put("Header", headerInner);
        rawPredicates.put("1", headerOuter);

        // Representation C: Comma-separated Shortcut
        rawPredicates.put("2", "NotMethod=GET,OPTIONS");

        // 2. Instantiate the record using the constructor
        final RequestMatchingProcessor processor = new RequestMatchingProcessor(
                "test-matcher",
                rawPredicates,
                new LogOptions(null, null, null),
                new LogOptions(null, null, null)
        );

        // 3. Transform using ConfigUtil
        final List<PredicateConfig> configs = ConfigUtil.toMatchers(List.of(processor));
        final PredicateConfig config = configs.getFirst();

        // 4. SCENARIOS

        // SCENARIO 1: Valid POST (Matches Path, Matches Header, Matches NotMethod)
        MockHttpServletRequest request = createRequest("POST", "http://localhost/api/data", "localhost");
        request.addHeader("X-Request-Id", "999");
        assertThat(config.predicate().test(request))
                .as("Valid numeric ID and POST should match")
                .isTrue();

        // SCENARIO 2: Valid Path but Invalid Header Regex
        request = createRequest("POST", "http://localhost/api/data", "localhost");
        request.addHeader("X-Request-Id", "abc");
        assertThat(config.predicate().test(request))
                .as("Non-numeric ID should fail regex predicate")
                .isFalse();

        // SCENARIO 3: Valid Header but Negated Method (GET)
        request = createRequest("GET", "http://localhost/api/data", "localhost");
        request.addHeader("X-Request-Id", "123");
        assertThat(config.predicate().test(request))
                .as("GET is negated in NotMethod and should fail")
                .isFalse();
    }

    @Test
    void testNotHostLogic()
    {
        final Map<String, Object> rawPredicates = new LinkedHashMap<>();
        rawPredicates.put("0", "NotHost=admin.**,monitoring.**");

        final RequestMatchingProcessor processor = new RequestMatchingProcessor(
                "host-test",
                rawPredicates,
                null,
                null
        );

        final PredicateConfig config = ConfigUtil.toMatchers(List.of(processor)).getFirst();

        // Scenario 1: Allowed Host
        MockHttpServletRequest okReq = createRequest("POST", "/", "api.ethlo.com");
        assertThat(config.predicate().test(okReq)).isTrue();

        // Scenario 2: Negated Host (admin)
        MockHttpServletRequest adminReq = createRequest("POST", "/", "admin.ethlo.com");
        assertThat(config.predicate().test(adminReq)).isFalse();

        // Scenario 3: Negated Host (monitoring)
        MockHttpServletRequest monReq = createRequest("POST", "/", "monitoring.internal");
        assertThat(config.predicate().test(monReq)).isFalse();
    }

    private MockHttpServletRequest createRequest(String method, String url, String host)
    {
        URI uri = URI.create(url);
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri.getPath());
        request.setServerName(uri.getHost());
        request.addHeader("Host", host);
        return request;
    }
}