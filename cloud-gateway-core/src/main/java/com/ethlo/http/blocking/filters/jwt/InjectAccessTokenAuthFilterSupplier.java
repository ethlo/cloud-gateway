package com.ethlo.http.blocking.filters.jwt;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class InjectAccessTokenAuthFilterSupplier implements FilterSupplier
{
    private final TaskScheduler taskScheduler;

    public InjectAccessTokenAuthFilterSupplier(TaskScheduler taskScheduler)
    {
        this.taskScheduler = taskScheduler;
    }

    /**
     * This method name is what you will use in your application.yaml.
     * The @Shortcut annotation allows Spring to bind positional arguments from YAML
     * (e.g., InjectAccessTokenAuth=my-client-id,my-url) to the Config object.
     */
    @Configurable
    public static HandlerFilterFunction<ServerResponse, ServerResponse> injectAccessTokenAuth(
            InjectAccessTokenConfig config)
    {
        return new InjectAccessTokenFilter(config);
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            // We return the static method defined above
            return List.of(this.getClass().getMethod("injectAccessTokenAuth", InjectAccessTokenConfig.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException("Filter method 'InjectAccessTokenAuth' not found!", e);
        }
    }
}