package com.ethlo.http.filters;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.factory.AbstractChangeRequestUriGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

@Component
public class PathHostGatewayFilterFactory extends AbstractChangeRequestUriGatewayFilterFactory<PathHostGatewayFilterFactory.Config>
{
    public PathHostGatewayFilterFactory()
    {
        super(Config.class);
    }

    @Override
    protected Optional<URI> determineRequestUri(final ServerWebExchange exchange, final Config config)
    {
        final ServerHttpRequest req = exchange.getRequest();
        final String path = req.getURI().getRawPath();
        final String[] originalParts = StringUtils.tokenizeToStringArray(path, "/");

        final String serviceName = config.getServiceIndex() < originalParts.length ? originalParts[config.getServiceIndex()] : null;
        if (serviceName == null)
        {
            return Optional.empty();
        }

        if (config.getAllowedRegexp() == null || config.allowedRegexp.matcher(serviceName).matches())
        {
            final String subPath = "/" + Arrays.stream(originalParts).skip(config.serviceIndex + 1).collect(Collectors.joining("/"));
            final String newUri = config.getScheme() + "://" + serviceName + subPath;
            return Optional.of(URI.create(newUri));
        }
        return Optional.empty();
    }

    @Override
    public List<String> shortcutFieldOrder()
    {
        return List.of("serviceIndex", "allowedRegexp");
    }

    public static class Config
    {
        private Pattern allowedRegexp;
        private int serviceIndex = 0;

        private String scheme = "http";

        public int getServiceIndex()
        {
            return serviceIndex;
        }

        public Config setServiceIndex(final int serviceIndex)
        {
            this.serviceIndex = serviceIndex;
            return this;
        }

        public String getAllowedRegexp()
        {
            return allowedRegexp != null ? allowedRegexp.pattern() : null;
        }

        public Config setAllowedRegexp(final String allowedRegexp)
        {
            this.allowedRegexp = Pattern.compile(allowedRegexp);
            return this;
        }

        public String getScheme()
        {
            return scheme;
        }

        public Config setScheme(final String scheme)
        {
            this.scheme = scheme;
            return this;
        }
    }
}