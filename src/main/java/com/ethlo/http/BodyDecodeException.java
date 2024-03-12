package com.ethlo.http;

import java.io.IOException;
import java.io.UncheckedIOException;

public class BodyDecodeException extends UncheckedIOException
{
    public BodyDecodeException(final String message, IOException cause)
    {
        super(message, cause);
    }
}
