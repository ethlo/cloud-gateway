package com.ethlo.http.predicates;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class NotExtensionPredicateSupplier implements PredicateSupplier
{
    /**
     * Maps to "NotExtension" in YAML.
     * We leverage the static Extension method from our previous supplier and negate it.
     */
    public static RequestPredicate notExtension(ExtensionPredicateSupplier.Config config)
    {
        return ExtensionPredicateSupplier.extension(config).negate();
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(this.getClass().getMethod("notExtension", ExtensionPredicateSupplier.Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }
}