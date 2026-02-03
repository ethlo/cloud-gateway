package com.ethlo.http.filters.jwt;

import org.springframework.core.NestedRuntimeException;

public class TokenParseException extends NestedRuntimeException
{
    public TokenParseException(final String msg, final Exception e)
    {
        super(msg, e);
    }
}
