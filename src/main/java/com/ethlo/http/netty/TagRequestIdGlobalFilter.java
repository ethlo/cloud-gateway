package com.ethlo.http.netty;

import com.ethlo.http.HttpLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.match.RequestMatchingConfiguration;
import reactor.core.publisher.Mono;

@Component
public class TagRequestIdGlobalFilter implements GlobalFilter, Ordered
{
    public static final String REQUEST_ID_ATTRIBUTE_NAME = "gateway-request-id";

    private final HttpLogger httpLogger;
    private final DataBufferRepository dataBufferRepository;
    private final RequestMatchingConfiguration requestMatchingConfiguration;
    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdGlobalFilter.class);

    public TagRequestIdGlobalFilter(final HttpLogger httpLogger, final DataBufferRepository dataBufferRepository, final RequestMatchingConfiguration requestMatchingConfiguration)
    {
        this.httpLogger = httpLogger;
        this.dataBufferRepository = dataBufferRepository;
        this.requestMatchingConfiguration = requestMatchingConfiguration;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        if (requestMatchingConfiguration.matches(exchange.getRequest()))
        {
            final String requestId = exchange.getRequest().getId();
            logger.info("Tagging request id in Netty client context: {}", requestId);
            return chain.filter(exchange).contextWrite(ctx -> ctx.put(REQUEST_ID_ATTRIBUTE_NAME, requestId))
                    .doFinally(st ->
                    {
                        logger.info("Finally {} - {}", requestId, st);
                        httpLogger.terminated(exchange.getRequest(), exchange.getResponse(), dataBufferRepository.get(DataBufferRepository.Operation.REQUEST, requestId), dataBufferRepository.get(DataBufferRepository.Operation.RESPONSE, requestId));
                    });
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder()
    {
        return Integer.MIN_VALUE;
    }
}