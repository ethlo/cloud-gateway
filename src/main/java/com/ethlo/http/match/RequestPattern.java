package com.ethlo.http.match;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;

public class RequestPattern
{
    private final List<UriPattern> uris;
    private final List<HttpMethod> methods;
    private final boolean requestBody;
    private final boolean responseBody;

    public RequestPattern(final List<UriPattern> uris, final List<HttpMethod> methods, final boolean requestBody, final boolean responseBody)
    {
        this.uris = uris;
        this.methods = methods;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
    }

    public List<UriPattern> getUris()
    {
        return Optional.ofNullable(uris).orElse(Collections.emptyList());
    }

    public List<HttpMethod> getMethods()
    {
        return Optional.ofNullable(methods).orElse(Collections.emptyList());
    }

    public boolean isRequestBody()
    {
        return requestBody;
    }

    public boolean isResponseBody()
    {
        return responseBody;
    }

    public boolean matches(ServerHttpRequest request)
    {
        final HttpMethod method = request.getMethod();
        final boolean matchesMethod = getMethods().isEmpty() || getMethods().contains(method);
        final boolean matchesUri = uris == null || uris.isEmpty() || getUris().stream().anyMatch(uri -> uri.matches(request.getURI()));
        return matchesMethod && matchesUri;
    }
}
