package com.ethlo.http.logger;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.logger.delegate.SequentialDelegateLogger;

@Configuration
public class HttpLoggerCfg
{
    @Bean
    LoggingFilterService loggingFilterService(final HttpLoggingConfiguration httpLoggingConfiguration)
    {
        return new LoggingFilterService(httpLoggingConfiguration);
    }

    @Bean
    SequentialDelegateLogger sequentialDelegateLogger(final LoggingFilterService loggingFilterService, final GenericApplicationContext applicationContext, final HttpLoggingConfiguration httpLoggingConfiguration, List<HttpLoggerFactory> factories)
    {
        final List<HttpLogger> loggers = httpLoggingConfiguration.getProviders().entrySet().stream().map(entry ->
        {
            final String name = entry.getKey();
            return factories.stream().filter(f -> f.getName().equalsIgnoreCase(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No factory for logging provider '" + name + "'"))
                    .getInstance(loggingFilterService, entry.getValue(), (beanName, instance) ->
                    {
                        applicationContext.getBeanFactory().registerSingleton(beanName, instance);
                        return null;
                    });
        }).toList();
        return new SequentialDelegateLogger(loggers);
    }
}
