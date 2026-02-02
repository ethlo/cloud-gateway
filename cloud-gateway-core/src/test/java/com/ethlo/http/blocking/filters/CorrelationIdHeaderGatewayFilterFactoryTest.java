package com.ethlo.http.blocking.filters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.web.servlet.function.ServerResponse;

class CorrelationIdHeaderFilterTest extends com.ethlo.http.blocking.filters.AbstractFilterTest<CorrelationIdFilterSupplier.Config>
{
    private static final String HEADER_NAME = "X-Custom-Correlation-Id";

    @Override
    protected FilterSupplier filterSupplier()
    {
        return new CorrelationIdFilterSupplier();
    }

    @Override
    protected String getFilterName()
    {
        // Matches the method name in the FilterSupplier
        return "correlationIdHeader";
    }

    @Test
    void shouldAddCorrelationIdHeaderToResponse() throws Exception
    {
        final CorrelationIdFilterSupplier.Config config = new CorrelationIdFilterSupplier.Config();
        config.setHeaderName(HEADER_NAME);

        final ServerResponse response = execute(config);

        assertThat(response.statusCode().is2xxSuccessful()).isTrue();
        assertThat(response.headers().containsHeader(HEADER_NAME)).isTrue();
    }
}