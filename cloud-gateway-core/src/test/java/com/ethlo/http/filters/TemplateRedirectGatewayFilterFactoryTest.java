package com.ethlo.http.filters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.ethlo.http.blocking.filters.AbstractFilterTest;
import com.ethlo.http.blocking.filters.TemplateRedirectFilterSupplier;

class TemplateRedirectFilterSupplierTest extends AbstractFilterTest<TemplateRedirectFilterSupplier.Config>
{
    @Override
    protected FilterSupplier filterSupplier()
    {
        return new TemplateRedirectFilterSupplier();
    }

    @Override
    protected String getFilterName()
    {
        return "templateRedirect";
    }

    @Test
    void testRedirect() throws Exception
    {
        // Given
        final TemplateRedirectFilterSupplier.Config config = new TemplateRedirectFilterSupplier.Config()
                .setSource("/foo/bar/(?<myvar>[a-z]+)")
                .setTarget("http://foo/target/{{myvar}}?internal_param={{query.myparam[0]}}");

        final MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/foo/bar/baz");
        mockRequest.setQueryString("myparam=hello");
        mockRequest.addParameter("myparam", "hello");

        // When
        final ServerResponse response = execute(config, mockRequest);

        // Then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.headers().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("http://foo/target/baz?internal_param=hello");
    }

    @Test
    void testRedirectMissingParam() throws Exception
    {
        // Given
        final TemplateRedirectFilterSupplier.Config config = new TemplateRedirectFilterSupplier.Config()
                .setSource("/foo/bar/(?<myvar>[a-z]+)")
                .setTarget("http://foo/target/{{myvar}}?internal_param={{query.myparam[0]}}");

        final MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/foo/bar/baz");

        // When
        final ServerResponse response = execute(config, mockRequest);

        // Then
        assertThat(response.headers().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("http://foo/target/baz?internal_param=");
    }
}