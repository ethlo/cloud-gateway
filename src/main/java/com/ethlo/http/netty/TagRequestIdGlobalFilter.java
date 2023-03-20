package com.ethlo.http.netty;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.HttpLogger;
import com.ethlo.http.match.RequestMatchingConfiguration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class TagRequestIdGlobalFilter implements GlobalFilter, Ordered
{
    public static final String REQUEST_ID_ATTRIBUTE_NAME = "gateway-request-id";
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
        if (requestMatchingConfiguration.matches(exchange.getRequest()))
        {
            final String requestId = exchange.getRequest().getId();
            logger.info("Tagging request id in Netty client context: {}", requestId);
            return chain.filter(exchange).contextWrite(ctx -> ctx.put(REQUEST_ID_ATTRIBUTE_NAME, requestId))
                    .publishOn(Schedulers.boundedElastic())
                    .doFinally(st ->
                    {
                        logger.info("Completed {}", requestId);
                        try (final BufferedInputStream requestData = dataBufferRepository.get(DataBufferRepository.Operation.REQUEST, requestId);
                             final BufferedInputStream responseData = dataBufferRepository.get(DataBufferRepository.Operation.RESPONSE, requestId))
                        {
                            findBodyPositionInStream(requestData);
                            findBodyPositionInStream(responseData);
                            httpLogger.completed(exchange.getRequest(), exchange.getResponse(), requestData, responseData);

                            // NOT in finally, as we do not want to delete data if it has not been properly processed
                            dataBufferRepository.cleanup(requestId);
                        }
                        catch (IOException e)
                        {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
        return chain.filter(exchange);
    }

    private void findBodyPositionInStream(BufferedInputStream data) throws IOException
    {
        final byte[] buffer = new byte[65_535];
        int read;
        long position = 0;
        data.mark(65_535);
        while ((read = data.read(buffer)) != -1)
        {
            for (int idx = 3; idx < read; idx++)
            {
                if (buffer[idx - 3] == 13
                        && buffer[idx - 2] == 10
                        && buffer[idx - 1] == 13
                        && buffer[idx] == 10)
                {
                    data.reset();
                    data.skip(position + 4L);
                    return;
                }
                position += 1;
            }
        }
    }

    @Override
    public int getOrder()
    {
        return Integer.MIN_VALUE;
    }
}