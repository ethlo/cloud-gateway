package com.ethlo.http.blocking.predicates;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class NotMethodPredicateSupplier implements PredicateSupplier
{
    /**
     * Maps to "NotMethod" in YAML
     */
    public static RequestPredicate NotMethod(Config config)
    {
        // Convert the string method name to HttpMethod and negate the standard predicate
        final HttpMethod method = HttpMethod.valueOf(config.getMethod().toUpperCase());
        return GatewayRequestPredicates.method(method).negate();
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(this.getClass().getMethod("NotMethod", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Configuration POJO for YAML binding.
     * Matches the property "method" used in standard Method predicate.
     */
    public static class Config
    {
        private String method;

        public String getMethod()
        {
            return method;
        }

        public void setMethod(String method)
        {
            this.method = method;
        }
    }
}