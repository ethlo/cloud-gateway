package com.ethlo.http.predicates;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

@Component
public class ExtensionRoutePredicateFactory extends AbstractRoutePredicateFactory<ExtensionRoutePredicateFactory.Config>
{
    public ExtensionRoutePredicateFactory()
    {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder()
    {
        return List.of("extensions");
    }

    @Override
    public ShortcutType shortcutType()
    {
        return ShortcutType.GATHER_LIST;
    }


    @Override
    public Predicate<ServerWebExchange> apply(final Config config)
    {
        return (GatewayPredicate) serverWebExchange ->
        {
            final RequestPath path = serverWebExchange.getRequest().getPath();
            final List<PathContainer.Element> elements = path.pathWithinApplication().elements();
            final String filename = elements.get(elements.size() - 1).value();
            final String[] parts = filename.split("\\.");
            if (parts.length > 1)
            {
                if (config.getExtensions().isEmpty())
                {
                    // Empty list matches all
                    return true;
                }
                final String extension = parts[parts.length - 1];
                return config.getExtensions().stream().anyMatch(e -> e.equals(extension));
            }
            return false;
        };
    }

    @Validated
    public static class Config
    {
        private List<String> extensions = new ArrayList<>(0);

        public List<String> getExtensions()
        {
            return extensions;
        }

        public ExtensionRoutePredicateFactory.Config setExtensions(List<String> extensions)
        {
            this.extensions = extensions;
            return this;
        }
    }
}