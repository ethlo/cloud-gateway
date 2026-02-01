package com.ethlo.http.blocking.filters.ratelimiter;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimiterFilterSupplier implements FilterSupplier
{
    private static UserCredentialsKeyResolver keyResolver;
    private static final ConcurrentMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    public RateLimiterFilterSupplier(UserCredentialsKeyResolver keyResolver)
    {
        RateLimiterFilterSupplier.keyResolver = keyResolver;
    }

    public static HandlerFilterFunction<@NonNull ServerResponse, @NonNull ServerResponse> requestRateLimiter(Config config)
    {
        final long capacity = config.getReplenishRate();
        final long refillIntervalMs = config.getRefreshPeriod() * 1000L;

        return (request, next) -> {
            // 1. Attempt to resolve key
            final HttpServletResponse servletResponse = (HttpServletResponse) request.attributes().get("jakarta.servlet.http.HttpServletResponse");
            String key = keyResolver.apply(request, servletResponse);

            // 2. Handle missing key based on configuration
            if (key == null)
            {
                if (config.isDenyEmptyKey())
                {
                    return ServerResponse.status(HttpStatus.valueOf(config.getEmptyKeyStatusCode())).build();
                }
                // Fallback to IP if not denied
                key = request.servletRequest().getRemoteAddr();
            }

            // 3. Rate limiting logic
            final BucketState bucket = buckets.computeIfAbsent(key, k -> new BucketState(new AtomicLong(capacity), new AtomicLong(System.currentTimeMillis())));
            refill(bucket, capacity, refillIntervalMs);

            if (bucket.tokens().getAndDecrement() > 0)
            {
                return next.handle(request);
            }
            else
            {
                bucket.tokens().incrementAndGet();
                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", String.valueOf(config.getRefreshPeriod()))
                        .build();
            }
        };
    }

    private static void refill(BucketState bucket, long capacity, long refillIntervalMs)
    {
        long now = System.currentTimeMillis();
        long last = bucket.lastRefillTime().get();
        if (now - last > refillIntervalMs)
        {
            if (bucket.lastRefillTime().compareAndSet(last, now))
            {
                bucket.tokens().set(capacity);
            }
        }
    }

    @Override
    public Collection<Method> get()
    {
        try
        {
            return List.of(this.getClass().getMethod("requestRateLimiter", Config.class));
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException("Filter method not found!", e);
        }
    }

    private record BucketState(AtomicLong tokens, AtomicLong lastRefillTime) {}

    public static class Config
    {
        private int replenishRate = 10;
        private int refreshPeriod = 1;
        private boolean denyEmptyKey = false;
        private int emptyKeyStatusCode = 403;

        public int getReplenishRate() { return replenishRate; }
        public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }

        public int getRefreshPeriod() { return refreshPeriod; }
        public void setRefreshPeriod(int refreshPeriod) { this.refreshPeriod = refreshPeriod; }

        public boolean isDenyEmptyKey() { return denyEmptyKey; }
        public void setDenyEmptyKey(boolean denyEmptyKey) { this.denyEmptyKey = denyEmptyKey; }

        public int getEmptyKeyStatusCode() { return emptyKeyStatusCode; }
        public void setEmptyKeyStatusCode(int emptyKeyStatusCode) { this.emptyKeyStatusCode = emptyKeyStatusCode; }
    }
}