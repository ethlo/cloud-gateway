package com.ethlo.http.filters.correlation;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class CorrelationIdFilterSupplier implements FilterSupplier
{
    public static final String ATTR_HEADER_NAME = "correlation.header.name";

    public static HandlerFilterFunction<ServerResponse, ServerResponse> correlationIdHeader()
    {
        return correlationIdHeader(new Config());
    }

    @Configurable
    public static HandlerFilterFunction<ServerResponse, ServerResponse> correlationIdHeader(Config config)
    {
        return (request, next) -> {
            String requestId = (String) request.attribute("requestId").orElseThrow();

            // Pass data to servlet layer
            request.attributes().put(ATTR_HEADER_NAME, config.getHeaderName());
            request.attributes().put("correlationId", requestId);

            return next.handle(request);
        };
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(
                    getClass().getMethod("correlationIdHeader"),
                    getClass().getMethod("correlationIdHeader", Config.class)
            );
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalStateException(e);
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
