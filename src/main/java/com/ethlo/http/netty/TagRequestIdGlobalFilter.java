package com.ethlo.http.netty;

import static com.ethlo.http.netty.HttpMessageUtil.findBodyPositionInStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.HttpLogger;
import com.ethlo.http.match.RequestMatchingConfiguration;
import com.ethlo.http.match.RequestPattern;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

@Component
public class TagRequestIdGlobalFilter implements GlobalFilter, Ordered
{
    public static final String REQUEST_ID_ATTRIBUTE_NAME = "gateway-request-id";
    public static final String LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME = "log_capture_config";

    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdGlobalFilter.class);

    private final HttpLogger httpLogger;
    private final DataBufferRepository dataBufferRepository;
    private final RequestMatchingConfiguration requestMatchingConfiguration;

    public TagRequestIdGlobalFilter(final HttpLogger httpLogger, final DataBufferRepository dataBufferRepository, final RequestMatchingConfiguration requestMatchingConfiguration)
    {
        this.httpLogger = httpLogger;
        this.dataBufferRepository = dataBufferRepository;
        this.requestMatchingConfiguration = requestMatchingConfiguration;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        final Optional<RequestPattern> match = requestMatchingConfiguration.matches(exchange.getRequest());
        if (match.isPresent())
        {
            final String requestId = exchange.getRequest().getId();
            logger.debug("Tagging request id in Netty client context: {}", requestId);
            final ServerHttpRequest requestWithHeaders = exchange.getRequest().mutate().header(REQUEST_ID_ATTRIBUTE_NAME, requestId).build();
            return chain.filter(exchange.mutate()
                            .request(requestWithHeaders).build())
                    .contextWrite(ctx ->
                            ctx.put(REQUEST_ID_ATTRIBUTE_NAME, requestId)
                                    .put(LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME, match.get()))
                    .publishOn(Schedulers.boundedElastic())
                    .doFinally(st ->
                    {
                        if (st.equals(SignalType.ON_COMPLETE) || st.equals(SignalType.ON_ERROR))
                        {
                            handleCompletedRequest(exchange, requestId);
                        }
                        else
                        {
                            logger.warn("Signal type {} - {}", requestId, st);
                        }
                    });
        }
        return chain.filter(exchange);
    }

    private void handleCompletedRequest(ServerWebExchange exchange, String requestId)
    {
        logger.debug("Completed request {}", requestId);
        try (final BufferedInputStream requestData = dataBufferRepository.get(DataBufferRepository.Operation.REQUEST, requestId);
             final BufferedInputStream responseData = dataBufferRepository.get(DataBufferRepository.Operation.RESPONSE, requestId))
        {
            dataBufferRepository.finished(requestId);

            if (requestData != null)
            {
                findBodyPositionInStream(requestData);
            }

            if (responseData != null)
            {
                findBodyPositionInStream(responseData);
            }

            httpLogger.completed(exchange.getRequest(), exchange.getResponse(), requestData, responseData);

            // NOT in finally, as we do not want to delete data if it has not been properly processed
            dataBufferRepository.cleanup(requestId);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int getOrder()
    {
        return Integer.MIN_VALUE;
    }
}