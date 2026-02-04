package com.ethlo.http.filters.basic;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.ethlo.http.logger.RedactUtil;
import jakarta.validation.constraints.NotEmpty;

@Component
public class InjectBasicAuthFilterSupplier implements FilterSupplier
{
    private static final Logger logger = LoggerFactory.getLogger(InjectBasicAuthFilterSupplier.class);

    /**
     * The static operation method that the Gateway will discover.
     */
    @Configurable
    public static HandlerFilterFunction<ServerResponse, ServerResponse> injectBasicAuth(Config config)
    {
        final String base64Encoded = Base64.getEncoder().encodeToString(
                (config.getUsername() + ":" + config.getPassword()).getBytes(StandardCharsets.UTF_8)
        );
        final String authValue = "Basic " + base64Encoded;

        return (request, next) ->
        {
            // MVC uses ServerRequest.from(request) for mutations
            logger.debug("Sending Authorization header (redacted): Basic {}", RedactUtil.redact(base64Encoded, 3));

            ServerRequest mutatedRequest = ServerRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, authValue)
                    .build();

            return next.handle(mutatedRequest);
        };
    }

    /**
     * No-args version for default/empty YAML args
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> injectBasicAuth()
    {
        return injectBasicAuth(new Config());
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(
                    this.getClass().getMethod("injectBasicAuth"),
                    this.getClass().getMethod("injectBasicAuth", Config.class)
            );
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static class Config
    {
        @NotEmpty
        private String username;

        @NotEmpty
        private String password;

        public String getUsername()
        {
            return username;
        }

        public void setUsername(String username)
        {
            this.username = username;
        }

        public String getPassword()
        {
            return password;
        }

        public void setPassword(String password)
        {
            this.password = password;
        }
    }
}