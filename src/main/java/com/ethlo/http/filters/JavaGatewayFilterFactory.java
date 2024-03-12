package com.ethlo.http.filters;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import com.ethlo.http.util.JavaCompiledExpressionUtil;
import com.ethlo.http.util.JavaExpressionConfig;

@Component
public class JavaGatewayFilterFactory extends AbstractGatewayFilterFactory<JavaExpressionConfig>
{
    public JavaGatewayFilterFactory()
    {
        super(JavaExpressionConfig.class);
    }

    @Override
    public GatewayFilter apply(JavaExpressionConfig config)
    {
        return JavaCompiledExpressionUtil.load(config, GatewayFilter.class);
    }
}