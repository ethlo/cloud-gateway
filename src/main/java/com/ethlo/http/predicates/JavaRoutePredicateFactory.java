package com.ethlo.http.predicates;

import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.util.JavaCompiledExpressionUtil;
import com.ethlo.http.util.JavaExpressionConfig;

@Component
public class JavaRoutePredicateFactory extends AbstractRoutePredicateFactory<JavaExpressionConfig>
{
    public JavaRoutePredicateFactory()
    {
        super(JavaExpressionConfig.class);
    }

    @Override
    public Predicate<ServerWebExchange> apply(JavaExpressionConfig config)
    {
        return JavaCompiledExpressionUtil.load(config, Predicate.class);
    }
}