package com.ethlo.http.logger;

import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

public interface HttpLoggerFactory
{
    String getName();

    HttpLogger getInstance(Map<String, Object> configuration, BiFunction<String, Object, Object> beanRegistration);

    default <T> T load(Map<String, Object> configuration, Class<T> configType)
    {
        final ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(configuration);
        final Binder binder = new Binder(propertySource);
        return binder.bind("", configType).get();
    }
}