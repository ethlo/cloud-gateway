package com.ethlo.http.blocking.filters;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class CorrelationIdFilterSupplier implements FilterSupplier
{
    public static HandlerFilterFunction<ServerResponse, ServerResponse> correlationIdHeader()
    {
        return correlationIdHeader(new Config());
    }

    public static HandlerFilterFunction<@NonNull ServerResponse, @NonNull ServerResponse> correlationIdHeader(Config config)
    {
        return (request, next) -> {
            String id = (String) request.attribute("requestId").orElse(request.servletRequest().getHeader("X-Request-Id"));
            ServerResponse response = next.handle(request);
            if (id != null)
            {
                response.headers().add(config.getHeaderName(), id);
            }
            return response;
        };
    }

    @Override
    public Collection<Method> get() {
        try {
            return List.of(
                    this.getClass().getMethod("correlationIdHeader"),
                    this.getClass().getMethod("correlationIdHeader", Config.class)
            );
        } catch (NoSuchMethodException e) {
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