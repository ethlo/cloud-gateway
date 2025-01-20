package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class CorrelationIdHeaderGatewayFilterFactoryTest
{

    private final ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
    private final GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
    private CorrelationIdHeaderGatewayFilterFactory filterFactory;

    @BeforeEach
    void setUp()
    {
        // Initialize the configuration and filter factory
        filterFactory = new CorrelationIdHeaderGatewayFilterFactory();
        when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

    }

    @Test
    void shouldAddCorrelationIdHeaderToRequestAndResponse()
    {
        // Given
        final CorrelationIdHeaderGatewayFilterFactory.Config config = new CorrelationIdHeaderGatewayFilterFactory.Config();
        config.setHeaderName("X-Custom-Correlation-Id");

        final MockServerHttpRequest mockRequest = MockServerHttpRequest.get("/test").build();
        final ServerWebExchange exchange = MockServerWebExchange.from(mockRequest);
        final GatewayFilter filter = filterFactory.apply(config);

        // When
        filter.filter(exchange, filterChain).block();

        // Then
        final ServerHttpRequest actualRequest = captor.getValue().getRequest();
        final String requestId = mockRequest.getId();
        final HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        assertThat(actualRequest.getHeaders().getFirst(config.getHeaderName())).isEqualTo(requestId);
        assertThat(responseHeaders.getFirst(config.getHeaderName())).isEqualTo(requestId);
    }
}
