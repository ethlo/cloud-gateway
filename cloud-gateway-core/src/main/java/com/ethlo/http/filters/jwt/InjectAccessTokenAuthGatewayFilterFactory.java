package com.ethlo.http.filters.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.ethlo.http.logger.RedactUtil;
import jakarta.validation.constraints.NotEmpty;
import reactor.core.publisher.Mono;

@Component
public class InjectAccessTokenAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<InjectAccessTokenAuthGatewayFilterFactory.Config>
{
    private static final Logger logger = LoggerFactory.getLogger(InjectAccessTokenAuthGatewayFilterFactory.class);
    private final Jackson2JsonEncoder jacksonEncoder = new Jackson2JsonEncoder();
    protected DecodedJWT jwt;
    private JwtTokenService tokenService;
    private Config config;

    public InjectAccessTokenAuthGatewayFilterFactory()
    {
        super(Config.class);
    }

    private static Map<String, Object> getErrorResponse(ServerWebExchange exchange)
    {
        final Map<String, Object> errorAttributes = new LinkedHashMap<>();
        final HttpStatus errorStatus = HttpStatus.FORBIDDEN;
        errorAttributes.put("path", exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_PREDICATE_PATH_CONTAINER_ATTR).toString());
        errorAttributes.put("status", errorStatus);
        errorAttributes.put("message", errorStatus.getReasonPhrase());
        errorAttributes.put("requestId", exchange.getRequest().getId());
        return errorAttributes;
    }

    private Mono<Void> writeBodyJson(Object body, ServerWebExchange exchange)
    {
        return exchange.getResponse().writeWith(
                jacksonEncoder.encode(
                        Mono.just(body),
                        exchange.getResponse().bufferFactory(),
                        ResolvableType.forInstance(body),
                        MediaType.APPLICATION_JSON,
                        Hints.from(Hints.LOG_PREFIX_HINT, exchange.getLogPrefix())
                )
        );
    }

    @Override
    public GatewayFilter apply(Config config)
    {
        this.config = config;
        return new GatewayFilter()
        {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
            {
                if (jwt == null)
                {
                    logger.warn("No auth token to inject for request to {}", exchange.getRequest().getURI());
                    return writeBodyJson(getErrorResponse(exchange), exchange);
                    //return exchange.getResponse().setComplete();
                }
                else
                {
                    return Mono.just(jwt).flatMap(token ->
                            {
                                final String authValue = "Bearer " + jwt.getToken();
                                logger.debug("Sending Authorization header (redacted): Bearer {}", RedactUtil.redact(jwt.getToken(), 3));
                                exchange.getRequest().mutate().header(HttpHeaders.AUTHORIZATION, authValue);
                                return chain.filter(exchange);
                            })
                            .onErrorResume(error ->
                            {
                                logger.warn("Denying access: {}", error.getMessage(), error);
                                return writeBodyJson(getErrorResponse(exchange), exchange);
                                //return exchange.getResponse().setComplete();
                            })
                            .onErrorComplete();
                }
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

    @Scheduled(initialDelay = 0, fixedRate = 10_000)
    protected void scheduledRefreshAccessToken()
    {
        if (config == null)
        {
            logger.info("Waiting for configuration to be initialized");
            return;
        }

        if (tokenService == null)
        {
            this.tokenService = new JwtTokenService();
        }

        final long now = Instant.now().toEpochMilli();
        if (jwt == null || jwt.getExpiresAtAsInstant().toEpochMilli() - now < config.getMinimumTTL().toMillis())
        {
            // We do not have an access token, or we are getting too close to expiry, refresh it
            logger.debug("Refreshing access token for {}", config.tokenUrl);

            try
            {
                this.jwt = tokenService.fetchAccessToken(config.tokenUrl, config.refreshToken, config.clientId, config.clientSecret).block();
                logger.debug("Successfully fetched access token from {}", config.tokenUrl);
            }
            catch (TokenFetchException | TokenParseException exc)
            {
                logger.warn("Error refreshing access token from {}: {}", config.tokenUrl, exc.getMessage());
            }
        }
    }

    public Publisher<DecodedJWT> fetchAccessToken(Config config)
    {
        return tokenService.fetchAccessToken(config.tokenUrl, config.refreshToken, config.clientId, config.clientSecret);
    }

    public static class Config
    {
        private static final Duration DEFAULT_MINIMUM_TTL = Duration.ofMinutes(1);
        public String jwksUrl;
        @NotEmpty
        private String tokenUrl;

        @NotEmpty
        private String clientId;

        @NotEmpty
        private String refreshToken;

        private Duration minimumTTL;

        private String clientSecret;

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
            return Optional.ofNullable(minimumTTL).orElse(DEFAULT_MINIMUM_TTL);
        }

        public Config setMinimumTTL(final Duration minimumTTL)
        {
            this.minimumTTL = Optional.ofNullable(minimumTTL).orElse(DEFAULT_MINIMUM_TTL);
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

        public String getJwksUrl()
        {
            return jwksUrl;
        }

        public Config setJwksUrl(final String jwksUrl)
        {
            this.jwksUrl = jwksUrl;
            return this;
        }

        public String getClientId()
        {
            return clientId;
        }

        public Config setClientId(final String clientId)
        {
            this.clientId = clientId;
            return this;
        }

        public String getRefreshToken()
        {
            return refreshToken;
        }

        public Config setRefreshToken(final String refreshToken)
        {
            this.refreshToken = refreshToken;
            return this;
        }
    }
}