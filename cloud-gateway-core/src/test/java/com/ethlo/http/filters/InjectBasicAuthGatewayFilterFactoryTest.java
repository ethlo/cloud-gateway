package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
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

    @Override
    protected GatewayFilterFactory<InjectBasicAuthGatewayFilterFactory.Config> filterFactory()
    {
        return new InjectBasicAuthGatewayFilterFactory();
    }
}