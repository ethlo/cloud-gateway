package com.ethlo.http.util;

import java.io.IOException;
import java.io.InputStream;

public class HttpMessageUtil
{
    public static final int MAX_HEADER_SIZE = 65_535;
    public static final int CR = 13;
    public static final int LF = 10;
    public static final byte[] BODY_SEPARATOR = new byte[]{CR, LF, CR, LF};

    private HttpMessageUtil()
    {
    }

    public static long findBodyPositionInStream(InputStream data) throws IOException
    {
        final byte[] buffer = new byte[MAX_HEADER_SIZE];
        int read;
        long position = 0;
        data.mark(MAX_HEADER_SIZE);
        while ((read = data.read(buffer)) != -1)
        {
            for (int idx = 3; idx < read; idx++)
            {
                if (buffer[idx - 3] == CR
                        && buffer[idx - 2] == LF
                        && buffer[idx - 1] == CR
                        && buffer[idx] == LF)
                {
                    data.reset();
                    final long offset = position + 4L;
                    data.skip(offset);
                    return offset;
                }
                position += 1;
            }
        }
        return -1L;
    }
}
