package com.ethlo.http.filters;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class RequestIdHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<RequestIdHeaderGatewayFilterFactory.Config>
{
    public RequestIdHeaderGatewayFilterFactory()
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
                exchange.getRequest().getHeaders().set(config.getRequestHeaderName(), internalRequestId);
                return chain.filter(exchange);
            }

            @Override
            public String toString()
            {
                return RequestIdHeaderGatewayFilterFactory.class + "{requestHeaderName=" + config.getRequestHeaderName() + "}";
            }

            @Override
            public List<String> shortcutFieldOrder()
            {
                return List.of("requestHeaderName");
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
        private String requestHeaderName = "X-Internal-Request-Id";

        public String getRequestHeaderName()
        {
            return requestHeaderName;
        }

        public Config setRequestHeaderName(final String requestHeaderName)
        {
            this.requestHeaderName = requestHeaderName;
            return this;
        }
    }
}