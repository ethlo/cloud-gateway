package com.ethlo.http.blocking.filters;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class AbstractFilterTest<T>
{
    protected final HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    private final ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);

    protected abstract FilterSupplier filterSupplier();

    protected abstract String getFilterName();

    @BeforeEach
    void setUp() throws Exception
    {
        // Mock the next handler to return a 200 OK and capture the (potentially mutated) request
        when(next.handle(captor.capture())).thenReturn(ServerResponse.ok().build());
    }

    protected ServerRequest actualRequest()
    {
        return captor.getValue();
    }

    protected ServerResponse execute(T config) throws Exception
    {
        return execute(config, new MockHttpServletRequest("GET", "/anything"));
    }

    protected ServerResponse execute(T config, MockHttpServletRequest mockRequest) throws Exception
    {
        // 1. Create the ServerRequest from the mock servlet request
        final ServerRequest request = ServerRequest.create(mockRequest, List.of());

        // 2. Get the filter function from your FilterSupplier (using the @Configurable method logic)
        // Note: You may need to call your specific static factory method here
        // depending on how you've structured your concrete tests.
        final HandlerFilterFunction<ServerResponse, ServerResponse> filter =
                (HandlerFilterFunction<ServerResponse, ServerResponse>) filterSupplier()
                        .get()
                        .stream()
                        .filter(m -> m.getName().equals(getFilterName()) && m.getParameterCount() > 0)
                        .findFirst()
                        .map(m -> {
                            try
                            {
                                return m.invoke(null, config);
                            }
                            catch (Exception e)
                            {
                                throw new RuntimeException(e);
                            }
                        })
                        .orElseThrow();

        // 3. Execute the filter synchronously
        return filter.filter(request, next);
    }
}