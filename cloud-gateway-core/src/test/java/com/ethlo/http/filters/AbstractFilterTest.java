package com.ethlo.http.filters;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public abstract class AbstractFilterTest<T>
{
    protected final GatewayFilterChain filterChain = mock(GatewayFilterChain.class);
    private final ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

    protected abstract GatewayFilterFactory<T> filterFactory();

    @BeforeEach
    void setUp()
    {
        when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());
    }

    protected ServerHttpRequest actualRequest()
    {
        return captor.getValue().getRequest();
    }

    protected ServerWebExchange execute(T config)
    {
        return execute(config, MockServerHttpRequest.get("/anything"));
    }

    protected ServerWebExchange execute(T config, MockServerHttpRequest.BaseBuilder<?> mockRequest)
    {
        final ServerWebExchange exchange = MockServerWebExchange.from(mockRequest);
        final GatewayFilter filter = filterFactory().apply(config);
        filter.filter(exchange, filterChain).block();
        return exchange;
    }
}
