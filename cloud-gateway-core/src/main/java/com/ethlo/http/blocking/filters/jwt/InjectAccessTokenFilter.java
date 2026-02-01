package com.ethlo.http.blocking.filters.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.ethlo.http.logger.RedactUtil;

public class InjectAccessTokenFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>
{
    private static final Logger logger = LoggerFactory.getLogger(InjectAccessTokenFilter.class);

    private final JwtTokenService tokenService = new JwtTokenService();
    private final InjectAccessTokenConfig config;
    private volatile DecodedJWT jwt;

    public InjectAccessTokenFilter(InjectAccessTokenConfig config, TaskScheduler taskScheduler)
    {
        this.config = config;
        taskScheduler.scheduleWithFixedDelay(this::scheduledRefreshAccessToken, Duration.ofSeconds(10));
    }

    /**
     * Fixed signature to match HandlerFilterFunction<ServerResponse, ServerResponse>
     */
    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception
    {
        final DecodedJWT currentJwt = this.jwt;

        if (currentJwt == null)
        {
            logger.warn("No auth token to inject for request to {}", request.uri());
            return ServerResponse.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(getErrorResponse(request));
        }

        final String authValue = "Bearer " + currentJwt.getToken();
        logger.debug("Sending Authorization header (redacted): Bearer {}", RedactUtil.redact(currentJwt.getToken(), 3));

        // Create the mutated request with the new header
        final ServerRequest mutatedRequest = ServerRequest.from(request)
                .header(HttpHeaders.AUTHORIZATION, authValue)
                .build();

        return next.handle(mutatedRequest);
    }

    private Map<String, Object> getErrorResponse(ServerRequest request)
    {
        final Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("path", request.path());
        errorAttributes.put("status", HttpStatus.FORBIDDEN.value());
        errorAttributes.put("message", HttpStatus.FORBIDDEN.getReasonPhrase());
        errorAttributes.put("requestId", request.attribute("requestId").orElse("unknown"));
        return errorAttributes;
    }

    private void scheduledRefreshAccessToken()
    {
        final long now = Instant.now().toEpochMilli();
        final long expiresAt = jwt == null ? 0 : jwt.getExpiresAtAsInstant().toEpochMilli();

        if (jwt == null || expiresAt - now < config.getMinimumTTL().toMillis())
        {
            logger.debug("Attempting to refresh access token from {}", config.getTokenUrl());
            try
            {
                final DecodedJWT newToken = Objects.requireNonNull(tokenService.fetchAccessToken(
                        config.getTokenUrl(),
                        config.getRefreshToken(),
                        config.getClientId(),
                        config.getClientSecret()
                ));

                this.jwt = newToken;
                logger.debug("Access token {} refreshed successfully", newToken.getId());
            }
            catch (Exception exc)
            {
                logger.warn("Error refreshing access token from {}: {}", config.getTokenUrl(), exc.getMessage());
                jwt = null;
            }
        }
    }
}