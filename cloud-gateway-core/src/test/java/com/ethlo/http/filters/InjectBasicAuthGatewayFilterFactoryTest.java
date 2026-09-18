package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpHeaders;

class InjectBasicAuthGatewayFilterFactoryTest extends AbstractFilterTest<InjectBasicAuthGatewayFilterFactory.Config>
{
    @Test
    void shouldAddCorrelationIdHeaderToRequestAndResponse()
    {
        // Given
        final InjectBasicAuthGatewayFilterFactory.Config config = new InjectBasicAuthGatewayFilterFactory.Config();
        config.setUsername("me");
        config.setPassword("mypass");

        // When
        execute(config);

        // Then
        assertThat(actualRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Basic bWU6bXlwYXNz");
    }

    @Test
    void shouldSupportShorthandUsernameAndPasswordArguments()
    {
        final InjectBasicAuthGatewayFilterFactory factory = new InjectBasicAuthGatewayFilterFactory();
        final Map<String, String> args = new LinkedHashMap<>();
        args.put("_genkey_0", "me");
        args.put("_genkey_1", "mypass");

        final Map<String, Object> normalized = factory.shortcutType().normalize(args, factory, new SpelExpressionParser(), null);

        assertThat(normalized).containsOnly(
                entry("username", "me"),
                entry("password", "mypass"));
    }

    @Override
    protected GatewayFilterFactory<InjectBasicAuthGatewayFilterFactory.Config> filterFactory()
    {
        return new InjectBasicAuthGatewayFilterFactory();
    }
}