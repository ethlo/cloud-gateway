package com.ethlo.http.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    /**
     * Decode the body of the message and stream it to <code>out</code>, without holding the body in memory.
     *
     * @return The number of body bytes written
     */
    public static long writeBody(final InputStream fullMessage, final ServerDirection serverDirection, final OutputStream out) throws IOException
    {
        final HttpMessage message;
        try
        {
            message = serverDirection == ServerDirection.REQUEST ? rawHttp.parseRequest(fullMessage) : rawHttp.parseResponse(fullMessage);
        }
        catch (InvalidHttpRequest | InvalidHttpResponse exc)
        {
            throw new BodyDecodeException(exc.getMessage(), new IOException(exc));
        }

        if (message.getBody().isEmpty())
        {
            return 0;
        }

        final CountingOutputStream counting = new CountingOutputStream(out);
        try
        {
            message.getBody().get().writeDecodedTo(counting);
        }
        catch (IOException exc)
        {
            if (counting.failure != null)
            {
                throw counting.failure;
            }
            throw new BodyDecodeException(exc.getMessage(), exc);
        }
        catch (RuntimeException exc)
        {
            throw new BodyDecodeException(exc.getMessage(), new IOException(exc));
        }
        return counting.count;
    }

    /**
     * Counts the bytes written and remembers write failures, so they are not mistaken for an undecodable body.
     */
    private static class CountingOutputStream extends OutputStream
    {
        private final OutputStream delegate;
        private long count;
        private IOException failure;

        private CountingOutputStream(final OutputStream delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void write(final int b) throws IOException
        {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException
        {
            try
            {
                delegate.write(b, off, len);
                count += len;
            }
            catch (IOException exc)
            {
                failure = exc;
                throw exc;
            }
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
