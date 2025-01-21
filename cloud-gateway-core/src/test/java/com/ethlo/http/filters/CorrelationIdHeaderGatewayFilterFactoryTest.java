package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

class CorrelationIdHeaderGatewayFilterFactoryTest extends AbstractFilterTest<CorrelationIdHeaderGatewayFilterFactory.Config>
{
    @Test
    void shouldAddCorrelationIdHeaderToRequestAndResponse()
    {
        // Given
        final CorrelationIdHeaderGatewayFilterFactory.Config config = new CorrelationIdHeaderGatewayFilterFactory.Config();
        config.setHeaderName("X-Custom-Correlation-Id");

        final ServerWebExchange exchange = execute(config);
        final ServerHttpRequest mockRequest = exchange.getRequest();

        // Then
        final ServerHttpRequest actualRequest = super.actualRequest();
        final String requestId = mockRequest.getId();
        final HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        assertThat(actualRequest.getHeaders().getFirst(config.getHeaderName())).isEqualTo(requestId);
        assertThat(responseHeaders.getFirst(config.getHeaderName())).isEqualTo(requestId);
    }

    @Override
    protected GatewayFilterFactory<CorrelationIdHeaderGatewayFilterFactory.Config> filterFactory()
    {
        return new CorrelationIdHeaderGatewayFilterFactory();
    }
}
