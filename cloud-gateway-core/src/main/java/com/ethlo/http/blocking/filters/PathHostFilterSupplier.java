package com.ethlo.http.blocking.filters;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class PathHostFilterSupplier implements FilterSupplier
{
    @Configurable
    public static HandlerFilterFunction<ServerResponse, ServerResponse> pathHost(Config config)
    {
        return (request, next) ->
        {
            final String path = request.uri().getRawPath();
            final String[] originalParts = StringUtils.tokenizeToStringArray(path, "/");

            if (config.getServiceIndex() < originalParts.length)
            {
                final String serviceName = originalParts[config.getServiceIndex()];

                if (config.getAllowedRegexp() == null || config.allowedRegexp.matcher(serviceName).matches())
                {
                    final String subPath = "/" + Arrays.stream(originalParts)
                            .skip(config.getServiceIndex() + 1)
                            .collect(Collectors.joining("/"));
                    
                    final String newUri = config.getScheme() + "://" + serviceName + subPath;
                    
                    // In MVC, we set the target URI as a request attribute
                    request.attributes().put(MvcUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(newUri));
                }
            }
            return next.handle(request);
        };
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(PathHostFilterSupplier.class.getMethod("pathHost", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static class Config
    {
        private Pattern allowedRegexp;
        private int serviceIndex = 0;
        private String scheme = "http";

        public int getServiceIndex() { return serviceIndex; }

        public Config setServiceIndex(final int serviceIndex)
        {
            this.serviceIndex = serviceIndex;
            return this;
        }

        public String getAllowedRegexp() { return allowedRegexp != null ? allowedRegexp.pattern() : null; }

        public Config setAllowedRegexp(final String allowedRegexp)
        {
            this.allowedRegexp = Pattern.compile(allowedRegexp);
            return this;
        }

        public String getScheme() { return scheme; }

        public Config setScheme(final String scheme)
        {
            this.scheme = scheme;
            return this;
        }
    }
}