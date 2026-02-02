package com.ethlo.http.blocking.predicates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

class ExtensionRoutePredicateSupplierTest
{
    @Test
    void matchEmptyAll()
    {
        final ExtensionPredicateSupplier.Config config = new ExtensionPredicateSupplier.Config()
                .setExtensions(List.of());
        final ServerRequest request = createRequest("/hello/there/image.jpg");
        assertThat(ExtensionPredicateSupplier.extension(config).test(request)).isTrue();
    }

    @Test
    void matchSpecific()
    {
        final ExtensionPredicateSupplier.Config config = new ExtensionPredicateSupplier.Config()
                .setExtensions(List.of("jpg"));

        final ServerRequest request = createRequest("/hello/there/image.jpg");

        assertThat(ExtensionPredicateSupplier.extension(config).test(request)).isTrue();
    }

    @Test
    void nonMatchSpecific()
    {
        final ExtensionPredicateSupplier.Config config = new ExtensionPredicateSupplier.Config()
                .setExtensions(List.of("gif"));

        final ServerRequest request = createRequest("/hello/there/image.jpg");

        assertThat(ExtensionPredicateSupplier.extension(config).test(request)).isFalse();
    }

    @Test
    void emptyExtensionPath()
    {
        final ExtensionPredicateSupplier.Config config = new ExtensionPredicateSupplier.Config()
                .setExtensions(List.of("gif"));

        final ServerRequest request = createRequest("/hello/there/image.");

        assertThat(ExtensionPredicateSupplier.extension(config).test(request)).isFalse();
    }

    private ServerRequest createRequest(String path)
    {
        // Creates a synchronous ServerRequest from a MockHttpServletRequest
        return ServerRequest.create(new MockHttpServletRequest("GET", path), List.of());
    }
}