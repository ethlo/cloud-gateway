package com.ethlo.http.blocking.filters.ratelimiter;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.ethlo.http.blocking.configuration.BeanProvider;
import com.ethlo.http.blocking.filters.RequestKeyResolver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

@Component
public class RateLimiterFilterSupplier implements FilterSupplier
{
    private static final Cache<@NonNull String, Bucket> BUCKETS = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .build();

    @Configurable
    public static HandlerFilterFunction<@NonNull ServerResponse, @NonNull ServerResponse> requestRateLimiter(Config config)
    {
        return (request, next) ->
        {
            final String routeId = request.attribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR)
                    .map(Object::toString).orElse("default");

            String userKey = config.getKeyResolver().apply(request);

            if (userKey == null || userKey.isBlank())
            {
                if (config.isDenyEmptyKey())
                {
                    return ServerResponse.status(config.getEmptyKeyStatus())
                            .header(config.getReasonHeader(), "missing-key")
                            .build();
                }
                userKey = request.remoteAddress().map(InetSocketAddress::toString).orElse("unknown");
            }

            final String namespacedKey = routeId + ":" + userKey;
            final Bucket bucket = Objects.requireNonNull(BUCKETS.get(namespacedKey, k -> createNewBucket(config)));

            final boolean consumed = bucket.tryConsume(1);
            final long remaining = bucket.getAvailableTokens();

            if (consumed)
            {
                ServerResponse response = next.handle(request);
                response.headers().add("X-RateLimit-Limit", String.valueOf(config.getReplenishRate()));
                response.headers().add("X-RateLimit-Remaining", String.valueOf(remaining));
                return response;
            }

            if (config.isLogDenials())
            {
                request.servletRequest().setAttribute("RATE_LIMIT_DENIED", true);
            }

            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Limit", String.valueOf(config.getReplenishRate()))
                    .header("X-RateLimit-Remaining", String.valueOf(remaining))
                    .header("Retry-After", String.valueOf(config.getRefreshPeriod()))
                    .header(config.getReasonHeader(), config.getDenialReason())
                    .build();
        };
    }

    private static Bucket createNewBucket(Config config)
    {
        final Bandwidth limit = Bandwidth.builder()
                .capacity(config.getBurstCapacity())
                .refillGreedy(config.getReplenishRate(), Duration.ofSeconds(config.getRefreshPeriod()))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(RateLimiterFilterSupplier.class.getMethod("requestRateLimiter", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static class Config
    {
        private int replenishRate = 10;
        private int refreshPeriod = 1;
        private int burstCapacity = 10;
        private boolean denyEmptyKey = true;
        private int emptyKeyStatus = 403;
        private String reasonHeader = "RateLimit-Reason";
        private String denialReason = "quota-exceeded";
        private boolean logDenials = true;

        public int getReplenishRate()
        {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate)
        {
            this.replenishRate = replenishRate;
        }

        public int getRefreshPeriod()
        {
            return refreshPeriod;
        }

        public void setRefreshPeriod(int refreshPeriod)
        {
            this.refreshPeriod = refreshPeriod;
        }

        public int getBurstCapacity()
        {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity)
        {
            this.burstCapacity = burstCapacity;
        }

        public boolean isDenyEmptyKey()
        {
            return denyEmptyKey;
        }

        public void setDenyEmptyKey(boolean denyEmptyKey)
        {
            this.denyEmptyKey = denyEmptyKey;
        }

        public int getEmptyKeyStatus()
        {
            return emptyKeyStatus;
        }

        public void setEmptyKeyStatus(int emptyKeyStatus)
        {
            this.emptyKeyStatus = emptyKeyStatus;
        }

        public String getReasonHeader()
        {
            return reasonHeader;
        }

        public void setReasonHeader(String reasonHeader)
        {
            this.reasonHeader = reasonHeader;
        }

        public String getDenialReason()
        {
            return denialReason;
        }

        public void setDenialReason(String denialReason)
        {
            this.denialReason = denialReason;
        }

        public boolean isLogDenials()
        {
            return logDenials;
        }

        public void setLogDenials(boolean logDenials)
        {
            this.logDenials = logDenials;
        }

        public RequestKeyResolver getKeyResolver()
        {
            return BeanProvider.get(RequestKeyResolver.class);
        }
    }
}