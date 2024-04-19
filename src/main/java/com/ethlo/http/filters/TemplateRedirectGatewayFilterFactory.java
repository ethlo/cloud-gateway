package com.ethlo.http.filters;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.PebbleRenderer;
import com.google.common.annotations.Beta;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Beta
@Component
public class TemplateRedirectGatewayFilterFactory extends AbstractGatewayFilterFactory<TemplateRedirectGatewayFilterFactory.Config>
{
    private static final Logger logger = LoggerFactory.getLogger(TemplateRedirectGatewayFilterFactory.class);

    public TemplateRedirectGatewayFilterFactory()
    {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(final Config config)
    {
        return (exchange, chain) ->
        {
            if (!ServerWebExchangeUtils.isAlreadyRouted(exchange))
            {
                final Optional<URI> match = determineRequestUri(exchange, config);
                if (match.isPresent())
                {
                    logger.debug("Matched '{}' redirecting with {} to '{}'", config.source.pattern(), config.status, match.get());
                    exchange.getResponse().setStatusCode(config.status);
                    exchange.getResponse().getHeaders().add(HttpHeaders.LOCATION, match.get().toString());
                    ServerWebExchangeUtils.setAlreadyRouted(exchange);
                }
            }
            return chain.filter(exchange);
        };
    }

    @Override
    public List<String> shortcutFieldOrder()
    {
        return List.of("source", "target", "status");
    }

    @Valid
    public static class Config
    {
        private final PebbleRenderer pebbleRenderer = new PebbleRenderer(false);
        @NotNull
        private Pattern source;

        @NotNull
        private PebbleTemplate target;

        private HttpStatusCode status = HttpStatusCode.valueOf(302);

        public void setSource(final String source)
        {
            this.source = Pattern.compile(source);
        }

        public void setTarget(final String target)
        {
            this.target = pebbleRenderer.compile(target);
        }

        public void setStatus(final int status)
        {
            this.status = HttpStatusCode.valueOf(status);
            Assert.isTrue(this.status.is3xxRedirection(), "Must use a redirection status code, got " + status);
        }
    }

    protected Optional<URI> determineRequestUri(final ServerWebExchange exchange, final Config config)
    {
        final ServerHttpRequest req = exchange.getRequest();
        final String path = req.getURI().getRawPath();
        final Map<String, Object> data = new HashMap<>();
        final Matcher matcher = config.source.matcher(path);
        if (matcher.matches())
        {
            // Add request parameters
            data.put("query", exchange.getRequest().getQueryParams());

            // Add by index
            for (int i = 1; i <= matcher.groupCount(); i++)
            {
                data.put(Integer.toString(i), matcher.group(i));
            }

            // Add named
            final Map<String, Integer> named = matcher.namedGroups();
            for (Map.Entry<String, Integer> e : named.entrySet())
            {
                data.put(e.getKey(), matcher.group(e.getValue()));
            }

            final StringWriter sw = new StringWriter(64);
            try
            {
                config.target.evaluate(sw, data);
                return Optional.of(URI.create(sw.toString()));
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        return Optional.empty();
    }
}