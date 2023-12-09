package com.ethlo.http.predicates;

import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.util.JavaCompiledExpressionUtil;

@Component
public class JavaRoutePredicateFactory extends AbstractRoutePredicateFactory<JavaRoutePredicateFactory.Config>
{
    public JavaRoutePredicateFactory()
    {
        super(JavaRoutePredicateFactory.Config.class);
    }

    @Override
    public Predicate<ServerWebExchange> apply(JavaRoutePredicateFactory.Config config)
    {
        return JavaCompiledExpressionUtil.load(new ClassPathResource("/java/JavaRoutePredicateTemplate.java"), config.getExpression(), Predicate.class);
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