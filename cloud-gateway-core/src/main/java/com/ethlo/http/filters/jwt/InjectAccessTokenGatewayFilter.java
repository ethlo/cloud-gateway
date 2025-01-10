package com.ethlo.http.filters.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.server.ServerWebExchange;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ethlo.http.logger.RedactUtil;
import jakarta.annotation.Nonnull;
import reactor.core.publisher.Mono;

public class InjectAccessTokenGatewayFilter implements GatewayFilter
{
    private static final Logger logger = LoggerFactory.getLogger(InjectAccessTokenAuthGatewayFilterFactory.class);

    private final Jackson2JsonEncoder jacksonEncoder = new Jackson2JsonEncoder();
    private final JwtTokenService tokenService = new JwtTokenService();
    private final InjectAccessTokenConfig config;
    protected DecodedJWT jwt;

    public InjectAccessTokenGatewayFilter(InjectAccessTokenConfig config, TaskScheduler taskScheduler)
    {
        this.config = config;
        taskScheduler.scheduleWithFixedDelay(this::scheduledRefreshAccessToken, Duration.ofSeconds(10));
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        if (jwt == null)
        {
            logger.warn("No auth token to inject for request to {}", exchange.getRequest().getURI());
            return writeBodyJson(getErrorResponse(exchange), exchange);
        }
        else
        {
            return Mono.just(jwt).flatMap(token ->
            {
                final String authValue = "Bearer " + jwt.getToken();
                logger.debug("Sending Authorization header (redacted): Bearer {}", RedactUtil.redact(jwt.getToken(), 3));
                final ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().headers(httpHeaders -> httpHeaders.set(HttpHeaders.AUTHORIZATION, authValue)).build();
                final ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
                return chain.filter(mutatedExchange);
            });
        }
    }

    @Override
    public String toString()
    {
        return InjectAccessTokenAuthGatewayFilterFactory.class + "{client-id=" + config.getClientId()
                + ", token-url" + config.getTokenUrl() + "}";
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

    private void scheduledRefreshAccessToken()
    {
        final long now = Instant.now().toEpochMilli();
        final long expiresAt = jwt == null ? 0 : jwt.getExpiresAtAsInstant().toEpochMilli();
        if (jwt == null || expiresAt - now < config.getMinimumTTL().toMillis())
        {

            // We do not have an access token, or we are getting too close to expiry, refresh it
            logger.debug("Attempting to refresh access token from {}", config.getTokenUrl());

            try
            {
                final @Nonnull DecodedJWT newToken = Objects.requireNonNull(tokenService.fetchAccessToken(config.getTokenUrl(), config.getRefreshToken(), config.getClientId(), config.getClientSecret()).block());
                if (jwt == null)
                {
                    final DecodedJWT refreshToken = JWT.decode(config.getRefreshToken());
                    logger.debug("Refresh token {} will expire at: {}", refreshToken.getId(), refreshToken.getExpiresAtAsInstant() != null ? refreshToken.getExpiresAtAsInstant() : "never");
                    logger.debug("Access token {} fetched with expiry at {} from {}", newToken.getId(), newToken.getExpiresAtAsInstant(), config.getTokenUrl());
                }
                else
                {
                    logger.debug("Refreshed access token from {}", config.getTokenUrl());
                }
                this.jwt = newToken;
            }
            catch (Exception exc)
            {
                logger.warn("Error refreshing access token from {}: {}", config.getTokenUrl(), exc.getMessage());
                jwt = null;
            }
        }
    }

    public Publisher<DecodedJWT> fetchAccessToken()
    {
        return tokenService.fetchAccessToken(config.getTokenUrl(), config.getRefreshToken(), config.getClientId(), config.getClientSecret());
    }
}
