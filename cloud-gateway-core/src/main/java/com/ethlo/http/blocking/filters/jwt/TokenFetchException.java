package com.ethlo.http.blocking.filters.jwt;

import org.springframework.core.NestedRuntimeException;

public class TokenFetchException extends NestedRuntimeException
{
    public TokenFetchException(final String msg)
    {
        super(msg);
    }
}
