package com.ethlo.http.netty;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class RequestLogGlobalFilter implements GlobalFilter
{
    public static final String REQUEST_ID_ATTRIBUTE_NAME = "gateway-request-id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(REQUEST_ID_ATTRIBUTE_NAME, exchange.getRequest().getId()));
    }


}