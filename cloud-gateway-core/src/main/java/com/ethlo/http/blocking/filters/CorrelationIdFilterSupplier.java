package com.ethlo.http.blocking.filters;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class CorrelationIdFilterSupplier implements FilterSupplier
{
    public static HandlerFilterFunction<ServerResponse, ServerResponse> correlationIdHeader()
    {
        return correlationIdHeader(new Config());
    }

    @Configurable
    public static HandlerFilterFunction<ServerResponse, ServerResponse> correlationIdHeader(Config config)
    {
        return (request, next) ->
        {
            String requestId = (String) request.attribute("requestId").orElse("unknown");

            ServerRequest mutatedRequest = ServerRequest.from(request)
                    .header(config.getHeaderName(), requestId)
                    .build();

            ServerResponse response = next.handle(mutatedRequest);

            return ServerResponse.from(response)
                    .header(config.getHeaderName(), requestId)
                    .build();
        };
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(
                    this.getClass().getMethod("correlationIdHeader"),
                    this.getClass().getMethod("correlationIdHeader", Config.class)
            );
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static class Config
    {
        private String headerName = "X-Correlation-Id";

        public String getHeaderName()
        {
            return headerName;
        }

        public void setHeaderName(String headerName)
        {
            this.headerName = headerName;
        }
    }
}