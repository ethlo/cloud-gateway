package com.ethlo.http.predicates;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;

@Component
public class NotPathPredicateSupplier implements PredicateSupplier
{
    /**
     * Maps to "NotPath" in YAML
     */
    @Configurable
    public static RequestPredicate notPath(Config config)
    {
        RequestPredicate predicate = null;
        for (String pattern : config.getPatterns())
        {
            RequestPredicate pathPredicate = RequestPredicates.path(pattern);
            predicate = (predicate == null) ? pathPredicate : predicate.or(pathPredicate);
        }
        
        return predicate != null ? predicate.negate() : request -> true;
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(this.getClass().getMethod("notPath", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Config POJO for YAML. matches standard Path predicate args.
     */
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