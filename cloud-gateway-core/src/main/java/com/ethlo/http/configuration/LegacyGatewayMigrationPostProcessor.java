package com.ethlo.http.configuration;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
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
public class LegacyGatewayMigrationPostProcessor implements EnvironmentPostProcessor, ApplicationListener<@NonNull ApplicationReadyEvent>
{
    private static final DeferredLog logger = new DeferredLog();
    private static final String OLD_PREFIX = "spring.cloud.gateway.";
    private static final String NEW_PREFIX = "spring.cloud.gateway.server.webflux.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, @NotNull SpringApplication application)
    {
        final Map<String, Object> migratedProperties = new HashMap<>();

        // Iterate over all property sources (System props, Env vars, YAMLs, etc.)
        for (PropertySource<?> source : environment.getPropertySources())
        {
            if (source instanceof MapPropertySource mapSource)
            {
                for (String key : mapSource.getPropertyNames())
                {
                    // Check if property starts with old prefix but NOT the new one (to avoid double mapping)
                    // We exclude 'httpclient' or other submodules that might not have moved
                    if (key.startsWith(OLD_PREFIX) && !key.startsWith(NEW_PREFIX))
                    {
                        // We strictly only migrate "routes", "default-filters", and "discovery"
                        // as these are the main ones that moved to .server.webflux
                        String suffix = key.substring(OLD_PREFIX.length());
                        if (suffix.startsWith("routes") || suffix.startsWith("default-filters") || suffix.startsWith("discovery"))
                        {
                            String newKey = NEW_PREFIX + suffix;
                            // Only add if not already present (newer config takes precedence)
                            if (!environment.containsProperty(newKey))
                            {
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