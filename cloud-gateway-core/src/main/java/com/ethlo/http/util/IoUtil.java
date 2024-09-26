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

    public static String formatSize(long v)
    {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
