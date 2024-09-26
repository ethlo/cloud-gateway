package com.ethlo.http.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.commons.io.input.NullInputStream;

import com.ethlo.http.BodyDecodeException;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.netty.ServerDirection;
import rawhttp.core.HttpMessage;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.errors.InvalidHttpRequest;
import rawhttp.core.errors.InvalidHttpResponse;

public class HttpBodyUtil
{
    private static final RawHttp rawHttp = new RawHttp(getConfig());

    public static BodyProvider extractBody(final InputStream fullMessage, ServerDirection serverDirection) throws BodyDecodeException
    {
        HttpMessage message;
        try
        {
            message = serverDirection == ServerDirection.REQUEST ? rawHttp.parseRequest(fullMessage) : rawHttp.parseResponse(fullMessage);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
        catch (InvalidHttpRequest | InvalidHttpResponse exc)
        {
            throw new BodyDecodeException(exc.getMessage(), new IOException(exc));
        }

        if (message.getBody().isPresent())
        {
            try
            {
                final byte[] bodyBytes = message.getBody().get().decodeBody();
                return new BodyProvider(new ByteArrayInputStream(bodyBytes), bodyBytes.length);
            }
            catch (Exception exc)
            {
                throw new BodyDecodeException(exc.getMessage(), new IOException(exc));
            }
        }
        else
        {
            return new BodyProvider(new NullInputStream(), 0);
        }
    }

    private static RawHttpOptions getConfig()
    {
        final RawHttpOptions.Builder b = RawHttpOptions.newBuilder();
        b.allowContentLengthMismatch();
        b.withHttpHeadersOptions().withMaxHeaderValueLength(Integer.MAX_VALUE).withMaxHeaderNameLength(255);
        return b.build();
    }
}
