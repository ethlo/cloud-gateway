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

    public RequestPattern(final List<UriPattern> uris, final List<HttpMethod> methods)
    {
        this.uris = uris;
        this.methods = methods;
    }

    public List<UriPattern> getUris()
    {
        return Optional.ofNullable(uris).orElse(Collections.emptyList());
    }

    public List<HttpMethod> getMethods()
    {
        return Optional.ofNullable(methods).orElse(Collections.emptyList());
    }

    public boolean matches(ServerHttpRequest request)
    {
        final HttpMethod method = request.getMethod();
        final boolean matchesMethod = getMethods().isEmpty() || getMethods().contains(method);
        final boolean matchesUri = uris.isEmpty() || getUris().stream().anyMatch(uri -> uri.matches(request.getURI()));
        return matchesMethod && matchesUri;
    }
}
