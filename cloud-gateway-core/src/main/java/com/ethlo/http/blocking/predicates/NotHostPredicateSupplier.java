package com.ethlo.http.blocking.predicates;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class NotHostPredicateSupplier implements PredicateSupplier
{
    public static RequestPredicate NotHost(Config config)
    {
        // We use the MVC-specific host predicate and negate it
        return GatewayRequestPredicates.host(config.getPatterns().toArray(new String[0])).negate();
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(this.getClass().getMethod("NotHost", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException("Could not find NotHost method", e);
        }
    }

    public static class Config
    {
        private List<String> patterns = new ArrayList<>();

        public List<String> getPatterns()
        {
            return patterns;
        }

        public void setPatterns(List<String> patterns)
        {
            this.patterns = patterns;
        }
    }
}