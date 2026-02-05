package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import com.ethlo.http.filters.correlation.CorrelationIdFilterSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.web.servlet.function.ServerResponse;

class CorrelationIdHeaderFilterTest extends AbstractFilterTest<CorrelationIdFilterSupplier.Config>
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