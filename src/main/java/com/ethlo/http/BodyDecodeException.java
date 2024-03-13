package com.ethlo.http;

import java.io.IOException;

public class BodyDecodeException extends IOException
{
    public BodyDecodeException(final String message, IOException cause)
    {
        super(message, cause);
    }
}
