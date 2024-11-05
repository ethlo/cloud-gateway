package com.ethlo.http.filters;

import static java.net.URLEncoder.encode;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.validation.constraints.NotEmpty;
import net.logstash.logback.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class InjectAccessTokenAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<InjectAccessTokenAuthGatewayFilterFactory.Config>
{
    private static final Logger logger = LoggerFactory.getLogger(InjectAccessTokenAuthGatewayFilterFactory.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    protected DecodedJWT jwt;

    public InjectAccessTokenAuthGatewayFilterFactory(final HttpClient httpClient, final ObjectMapper objectMapper)
    {
        super(Config.class);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    private static String encodeFormData(Map<String, String> formData)
    {
        return formData.entrySet()
                .stream()
                .map(entry ->
                        encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                                + encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    @Override
    public GatewayFilter apply(Config config)
    {
        return new GatewayFilter()
        {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
            {
                return getAccessToken(config).flatMap(token ->
                        {
                            final String authValue = "Bearer " + jwt.getToken();
                            exchange.getRequest().getHeaders().set("Authorization", authValue);
                            return chain.filter(exchange);
                        }).onErrorResume(error ->
                        {
                            logger.warn("Denying access: {}", error.getMessage());
                            return handleError(exchange);
                        })
                        .onErrorComplete();
            }

            private Mono<Void> handleError(ServerWebExchange exchange)
            {
                // Create a JSON response body
                final Map<String, Object> errorAttributes = new LinkedHashMap<>();
                errorAttributes.put("timestamp", new Date());
                errorAttributes.put("path", exchange.getRequest().getPath().value());
                final HttpStatus errorStatus = HttpStatus.FORBIDDEN;
                errorAttributes.put("status", errorStatus.value());
                errorAttributes.put("error", errorStatus.getReasonPhrase());
                errorAttributes.put("requestId", exchange.getRequest().getId());

                final String errorJson;
                try
                {
                    errorJson = objectMapper.writeValueAsString(errorAttributes);
                }
                catch (JsonProcessingException e)
                {
                    throw new UncheckedIOException(e);
                }

                // Set the response headers
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_LENGTH, String.valueOf(errorJson.length()));

                // Write the JSON error response to the output buffer
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(errorJson.getBytes());
                return exchange.getResponse().writeWith(Mono.just(buffer));
            }

            @Override
            public String toString()
            {
                return InjectAccessTokenAuthGatewayFilterFactory.class + "{username: " + config.getTokenUrl() + "}";
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

    private Instant getNow()
    {
        return Instant.now();
    }

    public Mono<DecodedJWT> getAccessToken(Config config)
    {
        final long now = getNow().toEpochMilli();
        if (jwt == null || jwt.getExpiresAtAsInstant().toEpochMilli() - now < config.getMinimumTTL().toMillis())
        {
            // We do not have an access token, or we are getting too close to expiry, refresh it
            logger.debug("Refreshing token for {}", config.tokenUrl);
            return fetchAccessToken(config).map(token ->
            {
                this.jwt = token;
                return token;
            });
        }
        return Mono.just(jwt);
    }

    public Mono<DecodedJWT> fetchAccessToken(Config config)
    {
        final Map<String, String> reqBody = new TreeMap<>(Map.of(
                "grant_type", "refresh_token",
                "refresh_token", config.refreshToken
        ));

        return httpClient
                .headers(headers ->
                {
                    headers.set("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                    if (!StringUtils.isEmpty(config.getClientSecret()))
                    {
                        headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString((config.clientId + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8)));
                    }
                })
                .post()
                .uri(config.tokenUrl)
                .send((req, out) -> out.sendObject(Unpooled.wrappedBuffer(encodeFormData(reqBody).getBytes(StandardCharsets.UTF_8))))
                .responseSingle((response, body) ->
                {
                    if (response.status() == HttpResponseStatus.OK)
                    {
                        return body.map(b -> b.toString(StandardCharsets.UTF_8));
                    }
                    else
                    {
                        return body.map(b -> b.toString(StandardCharsets.UTF_8))
                                .flatMap(decodedBody -> Mono.error(new RuntimeException(
                                        "Failed to fetch access token. Upstream token endpoint status code was " + response.status().code() + ":\n" + decodedBody
                                )));
                    }
                })
                .flatMap(responseBody -> Mono.just(JWT.decode(responseBody)));
    }

    public static class Config
    {
        private static final Duration DEFAULT_MINIMUM_TTL = Duration.ofMinutes(1);
        @NotEmpty
        private String tokenUrl;

        @NotEmpty
        private String clientId;

        @NotEmpty
        private String refreshToken;

        private Duration minimumTTL;

        private String clientSecret;

        public Config setClientId(final String clientId)
        {
            this.clientId = clientId;
            return this;
        }

        public String getTokenUrl()
        {
            return tokenUrl;
        }

        public Config setTokenUrl(final String tokenUrl)
        {
            this.tokenUrl = tokenUrl;
            return this;
        }

        public Duration getMinimumTTL()
        {
            return minimumTTL;
        }

        public Config setMinimumTTL(final Duration minimumTTL)
        {
            this.minimumTTL = Optional.ofNullable(minimumTTL).orElse(DEFAULT_MINIMUM_TTL);
            return this;
        }

        public Config setRefreshToken(final String refreshToken)
        {
            this.refreshToken = refreshToken;
            return this;
        }

        public String getClientSecret()
        {
            return clientSecret;
        }

        public Config setClientSecret(final String clientSecret)
        {
            this.clientSecret = clientSecret;
            return this;
        }
    }
}