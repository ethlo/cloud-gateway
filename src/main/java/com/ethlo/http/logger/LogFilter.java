package com.ethlo.http.logger;

import com.ethlo.http.match.HeaderPredicate;

public class LogFilter
{
    private HeaderPredicate headers;

    public HeaderPredicate getHeaders()
    {
        return headers;
    }

    public LogFilter setHeaders(final HeaderPredicate headers)
    {
        this.headers = headers;
        return this;
    }

    @Override
    public String toString()
    {
        return "headers={" + headers + "}";
    }
}
