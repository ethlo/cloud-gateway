package com.ethlo.http.filters.jwt;

import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class InjectAccessTokenAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<InjectAccessTokenConfig>
{
    private final TaskScheduler taskScheduler;

    public InjectAccessTokenAuthGatewayFilterFactory(TaskScheduler taskScheduler)
    {
        super(InjectAccessTokenConfig.class);
        this.taskScheduler = taskScheduler;
    }

    @Override
    public InjectAccessTokenGatewayFilter apply(InjectAccessTokenConfig config)
    {
        return new InjectAccessTokenGatewayFilter(config, taskScheduler);
    }
}