package com.ethlo.http.blocking.filters;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.ethlo.http.PebbleRenderer;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Component
public class TemplateRedirectFilterSupplier implements FilterSupplier
{
    private static final Logger logger = LoggerFactory.getLogger(TemplateRedirectFilterSupplier.class);

    @Configurable
    public static HandlerFilterFunction<@NonNull ServerResponse, @NonNull ServerResponse> templateRedirect(Config config)
    {
        return (request, next) ->
        {
            final Optional<URI> match = determineRedirectUri(request, config);
            if (match.isPresent())
            {
                logger.debug("Redirecting to '{}' with status {}", match.get(), config.getStatus());
                return ServerResponse.status(config.getStatus())
                        .header(HttpHeaders.LOCATION, match.get().toString())
                        .build();
            }
            return next.handle(request);
        };
    }

    private static Optional<URI> determineRedirectUri(final ServerRequest request, final Config config)
    {
        final String path = request.uri().getRawPath();
        final Matcher matcher = config.getSource().matcher(path);

        if (matcher.matches())
        {
            final Map<String, Object> data = new HashMap<>();

            // Add request parameters
            data.put("query", request.params());

            // Add by index
            for (int i = 1; i <= matcher.groupCount(); i++)
            {
                data.put(Integer.toString(i), matcher.group(i));
            }

            // Add named groups
            final Map<String, Integer> named = matcher.namedGroups();
            for (Map.Entry<String, Integer> e : named.entrySet())
            {
                data.put(e.getKey(), matcher.group(e.getValue()));
            }

            try (final StringWriter sw = new StringWriter(64))
            {
                config.getTarget().evaluate(sw, data);
                return Optional.of(URI.create(sw.toString()));
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(TemplateRedirectFilterSupplier.class.getMethod("templateRedirect", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Valid
    public static class Config
    {
        private static final PebbleRenderer pebbleRenderer = new PebbleRenderer(false);

        @NotNull
        private Pattern source;

        @NotNull
        private PebbleTemplate target;

        private HttpStatusCode status = HttpStatusCode.valueOf(302);

        public Pattern getSource()
        {
            return source;
        }

        public Config setSource(final String source)
        {
            this.source = Pattern.compile(source);
            return this;
        }

        public PebbleTemplate getTarget()
        {
            return target;
        }

        public Config setTarget(final String target)
        {
            this.target = pebbleRenderer.compile(target);
            return this;
        }

        public HttpStatusCode getStatus()
        {
            return status;
        }

        public Config setStatus(final int status)
        {
            this.status = HttpStatusCode.valueOf(status);
            Assert.isTrue(this.status.is3xxRedirection(), "Must use a redirection status code, got " + status);
            return this;
        }
    }
}