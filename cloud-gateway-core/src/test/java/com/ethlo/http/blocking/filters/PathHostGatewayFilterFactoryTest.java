package com.ethlo.http.blocking.filters;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class PathHostFilterSupplierTest extends com.ethlo.http.blocking.filters.AbstractFilterTest<PathHostFilterSupplier.Config>
{
    @Override
    protected FilterSupplier filterSupplier()
    {
        return new PathHostFilterSupplier();
    }

    @Override
    protected String getFilterName()
    {
        return "pathHost";
    }

    @Test
    void firstPathPartIsHost() throws Exception
    {
        // Given
        final PathHostFilterSupplier.Config config = new PathHostFilterSupplier.Config()
                .setServiceIndex(0);

        // When
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/foo/bar/baz");
        execute(config, mockRequest);

        // Then - Check the attribute on the captured request
        final URI routedUri = (URI) actualRequest().attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR).orElse(null);
        assertThat(routedUri).isEqualTo(URI.create("http://foo/bar/baz"));
    }

    @Test
    void secondPathPartIsHost() throws Exception
    {
        // Given
        final PathHostFilterSupplier.Config config = new PathHostFilterSupplier.Config()
                .setServiceIndex(1)
                .setScheme("https");

        // When
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/foo/bar/baz");
        execute(config, mockRequest);

        // Then
        final URI routedUri = (URI) actualRequest().attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR).orElse(null);
        assertThat(routedUri).isEqualTo(URI.create("https://bar/baz"));
    }

    @Test
    void regexpHost() throws Exception
    {
        // Given
        final PathHostFilterSupplier.Config config = new PathHostFilterSupplier.Config()
                .setServiceIndex(1)
                .setScheme("https")
                .setAllowedRegexp("[a-z]+");

        // When
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/foo/bar/baz");
        execute(config, mockRequest);

        // Then
        final URI routedUri = (URI) actualRequest().attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR).orElse(null);
        assertThat(routedUri).isEqualTo(URI.create("https://bar/baz"));
    }

    @Test
    void pathOutOfBounds() throws Exception
    {
        // Given
        final PathHostFilterSupplier.Config config = new PathHostFilterSupplier.Config()
                .setServiceIndex(5);

        // When
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/foo/bar/baz");
        execute(config, mockRequest);

        // Then
        final URI routedUri = (URI) actualRequest().attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR).orElse(null);
        assertThat(routedUri).isNull();
    }
}