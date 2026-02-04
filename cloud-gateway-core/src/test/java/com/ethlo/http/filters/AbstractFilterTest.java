package com.ethlo.http.filters;

import ch.qos.logback.core.testUtil.RandomUtil;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractFilterTest<T>
{
    protected final HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    private final ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);

    protected abstract FilterSupplier filterSupplier();

    protected abstract String getFilterName();

    @BeforeEach
    void setUp() throws Exception
    {
        // Mock a ServerResponse
        ServerResponse mockResponse = mock(ServerResponse.class);
        HttpHeaders mutableHeaders = new HttpHeaders();

        // Stub headers() to return our mutable map
        when(mockResponse.headers()).thenReturn(mutableHeaders);

        // Stub any other methods used by your filter
        when(mockResponse.statusCode()).thenReturn(HttpStatus.OK);

        // Then have next.handle() return it
        when(next.handle(captor.capture())).thenReturn(mockResponse);
    }

    protected ServerRequest actualRequest()
    {
        return captor.getValue();
    }

    protected ServerResponse execute(T config) throws Exception
    {
        final ServerRequest request = ServerRequest.from(ServerRequest.create(new MockHttpServletRequest("GET", "/anything"), List.of()))
                .attribute("requestId", "request-id-" + RandomUtil.getPositiveInt())
                .build();

        return execute(config, request);
    }

    protected ServerResponse execute(T config, HttpServletRequest request) throws Exception
    {
        return execute(config, ServerRequest.create(request, List.of()));
    }

    protected ServerResponse execute(T config, ServerRequest request) throws Exception
    {
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