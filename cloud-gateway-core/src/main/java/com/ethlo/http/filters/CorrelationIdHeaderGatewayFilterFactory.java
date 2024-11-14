package com.ethlo.http.filters;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class CorrelationIdHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<CorrelationIdHeaderGatewayFilterFactory.Config>
{
    public CorrelationIdHeaderGatewayFilterFactory()
    {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config)
    {
        return new GatewayFilter()
        {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
            {
                final String internalRequestId = exchange.getRequest().getId();
                exchange.getRequest().mutate().header(config.getHeaderName(), internalRequestId);
                exchange.getResponse().getHeaders().set(config.getHeaderName(), internalRequestId);
                return chain.filter(exchange);
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