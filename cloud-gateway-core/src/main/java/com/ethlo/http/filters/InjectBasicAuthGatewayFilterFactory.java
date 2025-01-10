package com.ethlo.http.filters;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.http.logger.RedactUtil;
import jakarta.validation.constraints.NotEmpty;
import reactor.core.publisher.Mono;

@Component
public class InjectBasicAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<InjectBasicAuthGatewayFilterFactory.Config>
{
    private static final Logger logger = LoggerFactory.getLogger(InjectBasicAuthGatewayFilterFactory.class);

    public InjectBasicAuthGatewayFilterFactory()
    {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config)
    {
        return new GatewayFilter()
        {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
            {
                final String base64Encoded = Base64.getEncoder().encodeToString((config.getUsername() + ":" + config.getPassword()).getBytes(StandardCharsets.UTF_8));
                final String authValue = "Basic " + base64Encoded;
                logger.debug("Sending Authorization header (redacted): Basic {}", RedactUtil.redact(base64Encoded, 3));
                final ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().headers(httpHeaders -> httpHeaders.set(HttpHeaders.AUTHORIZATION, authValue)).build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }

            @Override
            public String toString()
            {
                return InjectBasicAuthGatewayFilterFactory.class + "{username: " + config.getUsername() + ", password=*******}";
            }

            @Override
            public List<String> shortcutFieldOrder()
            {
                return List.of("username", "password");
            }

            @Override
            public ShortcutType shortcutType()
            {
                return ShortcutType.GATHER_LIST;
            }
        };
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

        public void setUsername(final String username)
        {
            this.username = username;
        }

        public String getPassword()
        {
            return password;
        }

        public void setPassword(final String password)
        {
            this.password = password;
        }
    }
}