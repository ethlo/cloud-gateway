package com.ethlo.http.filters;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class LegacyPreserveHostHeaderFilterSupplier implements FilterSupplier
{

    /**
     * Alias for the new PreserveHost filter
     */
    @Configurable
    public static HandlerFilterFunction<ServerResponse, ServerResponse> preserveHostHeader()
    {
        return FilterFunctions.preserveHost();
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(this.getClass().getMethod("preserveHostHeader"));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }
}