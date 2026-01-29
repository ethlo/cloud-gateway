package com.ethlo.http.filters;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class CorrelationIdHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<CorrelationIdHeaderGatewayFilterFactory.@NonNull Config>
{
    public CorrelationIdHeaderGatewayFilterFactory()
    {
        super(Config.class);
    }

    @NotNull
    @Override
    public GatewayFilter apply(Config config)
    {
        return new GatewayFilter()
        {
            @NotNull
            @Override
            public Mono<@NonNull Void> filter(@NotNull ServerWebExchange exchange, @NotNull GatewayFilterChain chain)
            {
                final String internalRequestId = exchange.getRequest().getId();
                final ServerHttpRequest updatedRequest = exchange.getRequest().mutate().header(config.getHeaderName(), internalRequestId).build();
                exchange.getResponse().getHeaders().set(config.getHeaderName(), internalRequestId);
                return chain.filter(exchange.mutate().request(updatedRequest).build());
            }

            @Override
            public String toString()
            {
                return CorrelationIdHeaderGatewayFilterFactory.class + "{headerName=" + config.getHeaderName() + "}";
            }

            @Override
            public List<String> shortcutFieldOrder()
            {
                return List.of("headerName");
            }

            @Override
            public ShortcutType shortcutType()
            {
                return ShortcutType.GATHER_LIST;
            }
        };
    }

    public static class Config
    {
        private String headerName = "X-Correlation-Id";

        public String getHeaderName()
        {
            return headerName;
        }

        public Config setHeaderName(final String requestHeaderName)
        {
            this.headerName = requestHeaderName;
            return this;
        }
    }
}