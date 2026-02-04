package com.ethlo.http.predicates;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class ExtensionPredicateSupplier implements PredicateSupplier
{
    @Configurable
    public static RequestPredicate extension(Config config)
    {
        return request -> {
            final String path = request.uri().getPath();
            final int lastDotIndex = path.lastIndexOf('.');

            // If no dot or dot is the last character
            if (lastDotIndex == -1 || lastDotIndex == path.length() - 1)
            {
                return false;
            }

            final String extension = path.substring(lastDotIndex + 1);

            if (config.getExtensions().isEmpty())
            {
                return true;
            }

            return config.getExtensions().stream()
                    .anyMatch(e -> e.equalsIgnoreCase(extension));
        };
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(this.getClass().getMethod("extension", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static class Config
    {
        private List<String> extensions = new ArrayList<>();

        public List<String> getExtensions()
        {
            return extensions;
        }

        public Config setExtensions(List<String> extensions)
        {
            this.extensions = extensions;
            return this;
        }
    }
}