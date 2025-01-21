package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

class TemplateRedirectGatewayFilterFactoryTest extends AbstractFilterTest<TemplateRedirectGatewayFilterFactory.Config>
{

    @Override
    protected TemplateRedirectGatewayFilterFactory filterFactory()
    {
        return new TemplateRedirectGatewayFilterFactory();
    }

    @Test
    void testRedirect()
    {
        // Given
        final TemplateRedirectGatewayFilterFactory.Config config = new TemplateRedirectGatewayFilterFactory.Config()
                .setSource("/foo/bar/(?<myvar>[a-z]+)")
                .setTarget("http://foo/target/{{myvar}}?internal_param={{query.myparam[0]}}");
        // When
        final ServerWebExchange exchange = execute(config, MockServerHttpRequest.get("https://proxy.com/foo/bar/baz?myparam=hello"));

        // Then
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("http://foo/target/baz?internal_param=hello");
    }

    @Test
    void testRedirectMissingParam()
    {
        // Given
        final TemplateRedirectGatewayFilterFactory.Config config = new TemplateRedirectGatewayFilterFactory.Config()
                .setSource("/foo/bar/(?<myvar>[a-z]+)")
                .setTarget("http://foo/target/{{myvar}}?internal_param={{query.myparam[0]}}");
        // When
        final ServerWebExchange exchange = execute(config, MockServerHttpRequest.get("https://proxy.com/foo/bar/baz"));

        // Then
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("http://foo/target/baz?internal_param=");
    }
}