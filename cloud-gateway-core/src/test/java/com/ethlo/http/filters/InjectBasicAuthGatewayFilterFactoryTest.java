package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpHeaders;

import com.ethlo.http.filters.basic.InjectBasicAuthFilterSupplier;

class InjectBasicAuthGatewayFilterFactoryTest extends AbstractFilterTest<InjectBasicAuthFilterSupplier.Config>
{
    @Test
    void shouldInjectBasicAuth() throws Exception
    {
        // Given
        final InjectBasicAuthFilterSupplier.Config config = new InjectBasicAuthFilterSupplier.Config();
        config.setUsername("me");
        config.setPassword("mypass");

        // When
        execute(config);

        // Then
        assertThat(actualRequest().headers().firstHeader(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Basic bWU6bXlwYXNz");    }

    @Override
    protected FilterSupplier filterSupplier()
    {
        return new InjectBasicAuthFilterSupplier();
    }

    @Override
    protected String getFilterName()
    {
        return "injectBasicAuth";
    }
}