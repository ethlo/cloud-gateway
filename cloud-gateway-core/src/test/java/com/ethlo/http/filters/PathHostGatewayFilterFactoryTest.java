package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

class PathHostGatewayFilterFactoryTest extends AbstractFilterTest<PathHostGatewayFilterFactory.Config>
{
    @Override
    protected PathHostGatewayFilterFactory filterFactory()
    {
        return new PathHostGatewayFilterFactory();
    }

    @Test
    void firstPathPartIsHost()
    {
        // Given
        final PathHostGatewayFilterFactory.Config config = new PathHostGatewayFilterFactory.Config()
                .setServiceIndex(0);

        // When
        final ServerWebExchange exchange = execute(config, MockServerHttpRequest.get("https://proxy.com/foo/bar/baz"));

        // Then
        assertThat((URI) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)).isEqualTo(URI.create("http://foo/bar/baz"));
    }

    @Test
    void secondPathPartIsHost()
    {
        // Given
        final PathHostGatewayFilterFactory.Config config = new PathHostGatewayFilterFactory.Config()
                .setServiceIndex(1)
                .setScheme("https");

        // When
        final ServerWebExchange exchange = execute(config, MockServerHttpRequest.get("https://proxy.com/foo/bar/baz"));

        // Then
        assertThat((URI) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)).isEqualTo(URI.create("https://bar/baz"));
    }

    @Test
    void regexpHost()
    {
        // Given
        final PathHostGatewayFilterFactory.Config config = new PathHostGatewayFilterFactory.Config()
                .setServiceIndex(1)
                .setScheme("https")
                .setAllowedRegexp("[a-z]+");

        // When
        final ServerWebExchange exchange = execute(config, MockServerHttpRequest.get("https://proxy.com/foo/bar/baz"));

        // Then
        assertThat((URI) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)).isEqualTo(URI.create("https://bar/baz"));
    }

    @Test
    void pathOutOfBounds()
    {
        // Given
        final PathHostGatewayFilterFactory.Config config = new PathHostGatewayFilterFactory.Config()
                .setServiceIndex(5);
        // When
        final ServerWebExchange exchange = execute(config, MockServerHttpRequest.get("https://proxy.com/foo/bar/baz"));

        // Then
        assertThat((URI) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)).isNull();
    }
}