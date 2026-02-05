package com.ethlo.http.logger;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.support.GenericApplicationContext;

import com.ethlo.http.configuration.BeanProvider;
import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.logger.delegate.AsyncDelegateLogger;
import com.ethlo.http.logger.delegate.DelegateHttpLogger;
import com.ethlo.http.logger.delegate.SyncDelegateLogger;

@Configuration
public class HttpLoggerCfg
{
    @Lazy(false)
    @Bean
    public static BeanProvider beanProvider(ApplicationContext applicationContext)
    {
        BeanProvider.setApplicationContext(applicationContext);
        return null;
    }

    @Bean
    LoggingFilterService loggingFilterService(final HttpLoggingConfiguration httpLoggingConfiguration)
    {
        return new LoggingFilterService(httpLoggingConfiguration);
    }

    @DependsOn("beanProvider")
    @Bean
    DelegateHttpLogger sequentialDelegateLogger(final GenericApplicationContext applicationContext, final HttpLoggingConfiguration httpLoggingConfiguration, List<HttpLoggerFactory> factories)
    {
        final List<HttpLogger> loggers = Optional.ofNullable(httpLoggingConfiguration.getProviders())
                .map(providers -> providers.entrySet().stream().map(entry ->
                        {
                            final String name = entry.getKey();
                            return factories.stream().filter(f -> f.getName().equalsIgnoreCase(name)).findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException("No factory for logging provider '" + name + "'"))
                                    .getInstance(entry.getValue(), (beanName, instance) ->
                                            {
                                                applicationContext.getBeanFactory().registerSingleton(beanName, instance);
                                                return null;
                                            }
                                    );
                        })
                        .toList()).orElse(List.of());

        if (httpLoggingConfiguration.async())
        {
            return new AsyncDelegateLogger(loggers);
        }
        return new SyncDelegateLogger(loggers);
    }
}
