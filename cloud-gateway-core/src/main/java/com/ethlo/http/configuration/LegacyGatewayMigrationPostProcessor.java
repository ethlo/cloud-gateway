package com.ethlo.http.configuration;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Manually migrates "spring.cloud.gateway.*" properties to "spring.cloud.gateway.server.webflux.*"
 * to ensure backward compatibility for Docker users.
 */
public class LegacyGatewayMigrationPostProcessor implements EnvironmentPostProcessor, ApplicationListener<ApplicationReadyEvent>
{
    private static final DeferredLog logger = new DeferredLog();
    private static final String OLD_PREFIX = "spring.cloud.gateway.";
    private static final String NEW_PREFIX = "spring.cloud.gateway.server.webflux.";

    @Override
    public void postProcessEnvironment(final ConfigurableEnvironment environment, @NotNull final SpringApplication application)
    {
        final Map<String, Object> migratedProperties = new HashMap<>();

        for (final PropertySource<?> source : environment.getPropertySources())
        {
            if (source instanceof final MapPropertySource mapSource)
            {
                for (final String key : mapSource.getPropertyNames())
                {
                    final String normalizedKey = key.toLowerCase().replace('_', '.');

                    if (normalizedKey.startsWith(OLD_PREFIX) && !normalizedKey.startsWith(NEW_PREFIX))
                    {
                        final String suffix = normalizedKey.substring(OLD_PREFIX.length());

                        if (suffix.startsWith("routes") || suffix.startsWith("default-filters") || suffix.startsWith("defaultfilters") || suffix.startsWith("discovery"))
                        {
                            final String normalizedSuffix = suffix.startsWith("defaultfilters")
                                    ? "default-filters" + suffix.substring("defaultfilters".length())
                                    : suffix;

                            final String newKey = NEW_PREFIX + normalizedSuffix;

                            if (!environment.containsProperty(newKey))
                            {
                                // We pull the value using the original raw key, but store it under the new canonical dotted key
                                migratedProperties.put(newKey, mapSource.getProperty(key));
                            }
                        }
                    }
                }
            }
        }

        if (!migratedProperties.isEmpty())
        {
            environment.getPropertySources().addFirst(new MapPropertySource("legacyGatewayMigration", migratedProperties));
            logger.warn("Migrated " + migratedProperties.size() + " legacy gateway properties to new 'server.webflux' prefix.");
        }

        application.addListeners(this);
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event)
    {
        logger.replayTo(LegacyGatewayMigrationPostProcessor.class);
    }
}