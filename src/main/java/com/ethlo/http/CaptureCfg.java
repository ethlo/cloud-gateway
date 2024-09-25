package com.ethlo.http;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.handlers.CircuitBreakerHandler;
import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.logger.delegate.SequentialDelegateLogger;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.TagRequestIdGlobalFilter;
import com.ethlo.http.processors.LogPreProcessor;
import com.ethlo.http.util.ObservableScheduler;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@RefreshScope
@ConditionalOnProperty("http-logging.capture.enabled")
public class CaptureCfg
{
    @Bean
    public DataBufferRepository pooledFileDataBufferRepository(CaptureConfiguration captureConfiguration) throws IOException
    {
        return new DataBufferRepository(captureConfiguration);
    }

    @Bean
    public CircuitBreakerHandler circuitBreakerHandler(DataBufferRepository dataBufferRepository)
    {
        return new CircuitBreakerHandler(dataBufferRepository);
    }

    @Bean
    public ServerWebExchangeContextFilter serverWebExchangeContextFilter()
    {
        return new ServerWebExchangeContextFilter();
    }

    @Bean
    @RefreshScope
    public TagRequestIdGlobalFilter tagRequestIdGlobalFilter(final SequentialDelegateLogger httpLogger,
                                                             final DataBufferRepository dataBufferRepository,
                                                             final LogPreProcessor logPreProcessor,
                                                             final RoutePredicateLocator routePredicateLocator,
                                                             final HttpLoggingConfiguration httpLoggingConfiguration,
                                                             final Scheduler ioScheduler)
    {
        final List<PredicateConfig> predicateConfigs = httpLoggingConfiguration.getMatchers()
                .stream()
                .map(c -> new PredicateConfig(c.id(), routePredicateLocator.getPredicates(c.predicates()), c.request(), c.response()))
                .toList();
        return new TagRequestIdGlobalFilter(httpLoggingConfiguration, httpLogger, dataBufferRepository, logPreProcessor, predicateConfigs, ioScheduler);
    }

    @Bean
    RouterFunction<ServerResponse> routes(CircuitBreakerHandler circuitBreakerHandler)
    {
        return nest(path("/upstream-down"), route(RequestPredicates.all(), circuitBreakerHandler));
    }

    @Bean
    @RefreshScope
    public RoutePredicateLocator routePredicateLocator(final List<RoutePredicateFactory> predicateFactories, final ConfigurationService configurationService)
    {
        return new RoutePredicateLocator(predicateFactories, configurationService);
    }

    @Bean
    public ObservableScheduler ioScheduler(final HttpLoggingConfiguration httpLoggingConfiguration)
    {
        final int queueSize = Optional.ofNullable(httpLoggingConfiguration.getMaxQueueSize()).orElse(HttpLoggingConfiguration.DEFAULT_QUEUE_SIZE);
        final BlockingQueue<Runnable> linkedBlockingDeque = new LinkedBlockingDeque<>(queueSize);
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final int maxThreads = Optional.ofNullable(httpLoggingConfiguration.getMaxIoThreads()).orElse(HttpLoggingConfiguration.DEFAULT_THREAD_COUNT);
        final WaitForCapacityPolicy queueBlockPolicy = new WaitForCapacityPolicy();

        return new ObservableScheduler(Schedulers.fromExecutorService(new ThreadPoolExecutor(maxThreads, maxThreads, Integer.MAX_VALUE,
                TimeUnit.SECONDS, linkedBlockingDeque,
                (r ->
                {
                    final Thread t = new Thread(r);
                    t.setName("log-io-" + threadNumber.getAndIncrement());
                    return t;
                }),
                queueBlockPolicy
        )), linkedBlockingDeque, queueSize, queueBlockPolicy.rejectedDelayCounter, queueBlockPolicy.rejectedDelay);
    }

    static class WaitForCapacityPolicy implements RejectedExecutionHandler
    {
        private static final Logger logger = LoggerFactory.getLogger(WaitForCapacityPolicy.class);
        private final AtomicInteger rejectedDelayCounter = new AtomicInteger();
        private final AtomicLong rejectedDelay = new AtomicLong();

        private final Duration waitTimeout = Duration.ofSeconds(5);

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor)
        {
            try
            {
                logger.debug("Waiting for queue capacity");
                final long started = System.nanoTime();
                if (!threadPoolExecutor.getQueue().offer(runnable, waitTimeout.toMillis(), TimeUnit.MILLISECONDS))
                {
                    rejectedDelayCounter.incrementAndGet();
                    rejectedDelay.addAndGet(System.nanoTime() - started);
                    rejectedExecution(runnable, threadPoolExecutor);
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException(e);
            }
        }
    }
}
