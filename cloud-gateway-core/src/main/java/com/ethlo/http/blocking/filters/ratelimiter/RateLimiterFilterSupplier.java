package com.ethlo.http.blocking.filters.ratelimiter;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.ethlo.http.blocking.configuration.BeanProvider;
import com.ethlo.http.blocking.filters.RequestKeyResolver;

@Component
public class RateLimiterFilterSupplier implements FilterSupplier
{
    private static final ConcurrentMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    @Configurable
    public static HandlerFilterFunction<ServerResponse, ServerResponse> requestRateLimiter(Config config)
    {
        return (request, next) -> {
            int capacity = config.getReplenishRate();
            long refillIntervalMs = config.getRefreshPeriod() * 1000L;

            final String key = config.getKeyResolverImpl().apply(request);

            final BucketState bucket = buckets.computeIfAbsent(
                    key,
                    k -> new BucketState(new AtomicLong(capacity), new AtomicLong(System.currentTimeMillis()))
            );

            refill(bucket, capacity, refillIntervalMs);

            if (bucket.tokens().getAndDecrement() > 0)
            {
                return next.handle(request);
            }

            bucket.tokens().incrementAndGet();
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS).build();
        };
    }

    private static void refill(BucketState bucket, long capacity, long intervalMs)
    {
        long now = System.currentTimeMillis();
        long last = bucket.lastRefillTime().get();

        if (now - last >= intervalMs &&
                bucket.lastRefillTime().compareAndSet(last, now))
        {
            bucket.tokens().set(capacity);
        }
    }

    @Override
    public Collection<Method> get()
    {
        return List.of(RateLimiterFilterSupplier.class.getMethods());
    }

    private record BucketState(AtomicLong tokens, AtomicLong lastRefillTime)
    {
    }

    public static class Config
    {
        private int replenishRate;
        private int refreshPeriod;


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

        public RequestKeyResolver getKeyResolverImpl()
        {
            return BeanProvider.get(RequestKeyResolver.class);
        }
    }
}
