package com.ethlo.http.logger;

import java.util.Optional;

import com.ethlo.http.match.HeaderPredicate;

public class LogFilter
{
    private HeaderPredicate requestHeaders;
    private HeaderPredicate responseHeaders;

    public HeaderPredicate getRequestHeaders()
    {
        return requestHeaders;
    }

    public LogFilter setRequestHeaders(final HeaderPredicate requestHeaders)
    {
        this.requestHeaders = Optional.ofNullable(requestHeaders).orElse(new HeaderPredicate(null, null));
        return this;
    }

    public HeaderPredicate getResponseHeaders()
    {
        return responseHeaders;
    }

    public LogFilter setResponseHeaders(final HeaderPredicate responseHeaders)
    {
        this.responseHeaders = Optional.ofNullable(responseHeaders).orElse(new HeaderPredicate(null, null));
        return this;
    }

    @Override
    public String toString()
    {
        return "requestHeaders={" + requestHeaders + "}, responseHeaders={" + responseHeaders + "}";
    }
}
