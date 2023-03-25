package com.ethlo.http.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class IoUtil
{
    private IoUtil()
    {
    }

    public static byte[] readAllBytes(InputStream inputStream)
    {
        if (inputStream == null)
        {
            return null;
        }

        try
        {
            return inputStream.readAllBytes();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
