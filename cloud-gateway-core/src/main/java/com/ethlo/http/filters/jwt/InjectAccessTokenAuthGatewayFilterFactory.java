package com.ethlo.http.filters.jwt;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class InjectAccessTokenAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<InjectAccessTokenConfig>
{
    private final TaskScheduler taskScheduler;

    /**
     * Each filter instance schedules its own token refresh that cannot be cancelled, and this factory is
     * invoked again for every route refresh. Reusing the instance per configuration keeps the number of
     * scheduled refreshes bounded.
     */
    private final ConcurrentMap<InjectAccessTokenConfig, InjectAccessTokenGatewayFilter> filters = new ConcurrentHashMap<>();

    public InjectAccessTokenAuthGatewayFilterFactory(TaskScheduler taskScheduler)
    {
        super(InjectAccessTokenConfig.class);
        this.taskScheduler = taskScheduler;
    }

    @Override
    public InjectAccessTokenGatewayFilter apply(InjectAccessTokenConfig config)
    {
        return filters.computeIfAbsent(config, c -> new InjectAccessTokenGatewayFilter(c, taskScheduler));
    }
}