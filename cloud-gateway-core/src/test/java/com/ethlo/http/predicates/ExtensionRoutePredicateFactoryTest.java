package com.ethlo.http.predicates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ExtensionRoutePredicateFactoryTest
{
    @Test
    void matchEmptyAll()
    {
        final ExtensionRoutePredicateFactory.Config config = new ExtensionRoutePredicateFactory.Config().setExtensions(List.of());
        final MockServerWebExchange exchange = new MockServerWebExchange.Builder(MockServerHttpRequest.get("/hello/there/image.jpg").build()).build();
        assertThat(new ExtensionRoutePredicateFactory().apply(config).test(exchange)).isTrue();
    }

    @Test
    void matchSpecific()
    {
        final ExtensionRoutePredicateFactory.Config config = new ExtensionRoutePredicateFactory.Config().setExtensions(List.of("jpg"));
        final MockServerWebExchange exchange = new MockServerWebExchange.Builder(MockServerHttpRequest.get("/hello/there/image.jpg").build()).build();
        assertThat(new ExtensionRoutePredicateFactory().apply(config).test(exchange)).isTrue();
    }

    @Test
    void nonMatchSpecific()
    {
        final ExtensionRoutePredicateFactory.Config config = new ExtensionRoutePredicateFactory.Config().setExtensions(List.of("gif"));
        final MockServerWebExchange exchange = new MockServerWebExchange.Builder(MockServerHttpRequest.get("/hello/there/image.jpg").build()).build();
        assertThat(new ExtensionRoutePredicateFactory().apply(config).test(exchange)).isFalse();
    }

    @Test
    void emptyExtensionPath()
    {
        final ExtensionRoutePredicateFactory.Config config = new ExtensionRoutePredicateFactory.Config().setExtensions(List.of("gif"));
        final MockServerWebExchange exchange = new MockServerWebExchange.Builder(MockServerHttpRequest.get("/hello/there/image.").build()).build();
        assertThat(new ExtensionRoutePredicateFactory().apply(config).test(exchange)).isFalse();
    }
}