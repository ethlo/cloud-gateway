package com.ethlo.http.logger;

import java.util.Map;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.support.GenericApplicationContext;

public interface HttpLoggerFactory
{
    String getName();

    HttpLogger getInstance(GenericApplicationContext applicationContext, Map<String, Object> configuration);

    default <T> T load(Map<String, Object> configuration, Class<T> configType)
    {
        final ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(configuration);
        final Binder binder = new Binder(propertySource);
        return binder.bind("", configType).get();
    }
}