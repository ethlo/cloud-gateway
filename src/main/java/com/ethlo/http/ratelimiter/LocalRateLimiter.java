package com.ethlo.http.ratelimiter;

import static org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter.REMAINING_HEADER;
import static org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter.REPLENISH_RATE_HEADER;
import static org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter.REQUESTED_TOKENS_HEADER;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public class LocalRateLimiter extends AbstractRateLimiter<LocalRateLimiter.Config>
{
    public static final String CONFIGURATION_PROPERTY_NAME = "local-rate-limiter";
    public static final String REFRESH_PERIOD_HEADER = "X-RateLimit-Refresh-Period";
    private static final Logger logger = LoggerFactory.getLogger(LocalRateLimiter.class);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final RateLimiterRegistry rateLimiterRegistry;

    public LocalRateLimiter(ConfigurationService configurationService)
    {
        super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        this.initialized.compareAndSet(false, true);
    }

    @Override
    public Mono<Response> isAllowed(final String routeId, final String key)
    {
        if (!this.initialized.get())
        {
            throw new IllegalStateException("LocalRateLimiter is not initialized");
        }

        final Config routeConfig = loadConfiguration(routeId);
        final int replenishRate = routeConfig.getReplenishRate();
        final Duration refreshPeriod = routeConfig.getRefreshPeriod();
        final int tokensRequiredForRequest = routeConfig.getRequestedTokens();

        final io.github.resilience4j.ratelimiter.RateLimiter rateLimiter = rateLimiterRegistry
                .rateLimiter(key, RateLimiterConfig.custom()
                        .timeoutDuration(Duration.ZERO)
                        .limitRefreshPeriod(refreshPeriod)
                        .limitForPeriod(replenishRate)
                        .build());

        final boolean allowed = rateLimiter.acquirePermission(tokensRequiredForRequest);
        final Long tokensLeft = (long) rateLimiter.getMetrics().getAvailablePermissions();

        if (logger.isDebugEnabled())
        {
            logger.debug("Ratelimiter: key={}, allowed={}, tokens left={}", key, allowed, tokensLeft);
        }

        final Response response = new Response(allowed, getHeaders(routeConfig, tokensLeft));
        return Mono.just(response);
    }

    private Config loadConfiguration(String routeId)
    {
        Config routeConfig = getConfig().getOrDefault(routeId, new Config());

        if (routeConfig == null)
        {
            routeConfig = getConfig().get(RouteDefinitionRouteLocator.DEFAULT_FILTERS);
        }

        if (routeConfig == null)
        {
            throw new IllegalArgumentException("No Configuration found for route " + routeId + " or defaultFilters");
        }
        return routeConfig;
    }

    @NotNull
    public Map<String, String> getHeaders(Config config, Long tokensLeft)
    {
        final Map<String, String> headers = new HashMap<>();
        headers.put(REMAINING_HEADER, tokensLeft.toString());
        headers.put(REFRESH_PERIOD_HEADER, Long.toString(config.getRefreshPeriod().toSeconds()));
        headers.put(REPLENISH_RATE_HEADER, Integer.toString(config.getReplenishRate()));
        headers.put(REQUESTED_TOKENS_HEADER, Integer.toString(config.getRequestedTokens()));
        return headers;
    }

    @Validated
    public static class Config
    {
        @Min(1)
        private int replenishRate;

        private Duration refreshPeriod = Duration.ofSeconds(1);

        @Min(1)
        private int requestedTokens = 1;

        public int getReplenishRate()
        {
            return replenishRate;
        }

        public LocalRateLimiter.Config setReplenishRate(int replenishRate)
        {
            this.replenishRate = replenishRate;
            return this;
        }

        public Duration getRefreshPeriod()
        {
            return refreshPeriod;
        }

        public LocalRateLimiter.Config setRefreshPeriod(Duration refreshPeriod)
        {
            this.refreshPeriod = refreshPeriod;
            return this;
        }

        public int getRequestedTokens()
        {
            return requestedTokens;
        }

        public LocalRateLimiter.Config setRequestedTokens(int requestedTokens)
        {
            this.requestedTokens = requestedTokens;
            return this;
        }

        @Override
        public String toString()
        {
            return new ToStringCreator(this).append("replenishRate", replenishRate)
                    .append("refreshPeriod", refreshPeriod)
                    .append("requestedTokens", requestedTokens).toString();
        }
    }
}