package com.ethlo.http.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class LegacyGatewayMigrationPostProcessorTest
{
    @Test
    public void shouldMigrateUppercaseEnvironmentVariablesWithoutHyphen()
    {
        final StandardEnvironment environment = new StandardEnvironment();
        final SpringApplication application = new SpringApplication();
        final LegacyGatewayMigrationPostProcessor processor = new LegacyGatewayMigrationPostProcessor();

        // Simulate environment variables exactly as the OS presents them (POSIX compliant)
        final Map<String, Object> rawEnvVars = Map.of(
                "SPRING_CLOUD_GATEWAY_DEFAULTFILTERS_0_NAME", "CorrelationIdHeader",
                "SPRING_CLOUD_GATEWAY_ROUTES_0_ID", "route-from-env"
        );

        environment.getPropertySources().addFirst(new MapPropertySource("systemEnvironment", rawEnvVars));

        processor.postProcessEnvironment(environment, application);

        // Assert that the raw ENV keys correctly resolved to the new hyphenated and bracketed webflux namespace
        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].name")).isEqualTo("CorrelationIdHeader");
        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.routes[0].id")).isEqualTo("route-from-env");
    }

    @Test
    public void shouldMigrateStandardLowercaseProperties()
    {
        final StandardEnvironment environment = new StandardEnvironment();
        final SpringApplication application = new SpringApplication();
        final LegacyGatewayMigrationPostProcessor processor = new LegacyGatewayMigrationPostProcessor();

        // Simulate properties parsed from application.yaml
        final Map<String, Object> rawYamlProps = Map.of(
                "spring.cloud.gateway.default-filters[0].name", "AddRequestHeader",
                "spring.cloud.gateway.routes[0].id", "route-from-yaml"
        );

        environment.getPropertySources().addFirst(new MapPropertySource("applicationConfig", rawYamlProps));

        processor.postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].name")).isEqualTo("AddRequestHeader");
        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.routes[0].id")).isEqualTo("route-from-yaml");
    }
}