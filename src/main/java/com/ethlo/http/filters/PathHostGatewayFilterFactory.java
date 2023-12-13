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
        final String serviceName = originalParts[config.serviceIndex];
        if (config.allowedRegexp == null || config.allowedRegexp.matcher(serviceName).matches())
        {
            final String subPath = "/" + Arrays.stream(originalParts).skip(config.serviceIndex + 1).collect(Collectors.joining());
            final String newUri = req.getURI().getScheme() + "://" + serviceName + subPath;
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

        public int getServiceIndex()
        {
            return serviceIndex;
        }

        public void setServiceIndex(final int serviceIndex)
        {
            this.serviceIndex = serviceIndex;
        }

        public String getAllowedRegexp()
        {
            return allowedRegexp.pattern();
        }

        public void setAllowedRegexp(final String allowedRegexp)
        {
            this.allowedRegexp = Pattern.compile(allowedRegexp);
        }
    }
}