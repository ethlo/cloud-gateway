package com.ethlo.http.filters;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.ethlo.http.util.JavaCompiledExpressionUtil;

@Component
public class JavaGatewayFilterFactory extends AbstractGatewayFilterFactory<JavaGatewayFilterFactory.Config>
{
    public JavaGatewayFilterFactory()
    {
        super(JavaGatewayFilterFactory.Config.class);
    }

    @Override
    public GatewayFilter apply(Config config)
    {
        return JavaCompiledExpressionUtil.load(new ClassPathResource("/java/GatewayFilterTemplate.java"), config.getExpression(), GatewayFilter.class);
    }

    @Validated
    public static class Config
    {
        private final String expression;

        public Config(final String expression)
        {
            this.expression = expression;
        }

        public String getExpression()
        {
            return expression;
        }
    }
}