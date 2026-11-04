package com.ethlo.http.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.ethlo.http.BodyDecodeException;
import com.ethlo.http.netty.ServerDirection;

class HttpBodyUtilTest
{
    private static final String CHUNKED_RESPONSE = """
            HTTP/1.1 200 OK\r
            Transfer-Encoding: chunked\r
            \r
            5\r
            hello\r
            6\r
             world\r
            0\r
            \r
            """;

    private static final String TRUNCATED_CHUNKED_RESPONSE = """
            HTTP/1.1 200 OK\r
            Transfer-Encoding: chunked\r
            \r
            19\r
            event: message\ndata: hi\n\r
            """;

    @Test
    void writeBodyStreamsTheDecodedBody() throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final long written = HttpBodyUtil.writeBody(stream(CHUNKED_RESPONSE), ServerDirection.RESPONSE, out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("hello world");
        assertThat(written).isEqualTo(11);
        assertThat(out.toByteArray()).isEqualTo(HttpBodyUtil.extractBody(stream(CHUNKED_RESPONSE), ServerDirection.RESPONSE).data().readAllBytes());
    }

    @Test
    void writeBodyWithoutBodyWritesNothing() throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(HttpBodyUtil.writeBody(stream("GET / HTTP/1.1\r\nHost: x\r\n\r\n"), ServerDirection.REQUEST, out)).isZero();
        assertThat(out.size()).isZero();
    }

    @Test
    void writeBodyReportsUndecodableBody()
    {
        assertThatThrownBy(() -> HttpBodyUtil.writeBody(stream(TRUNCATED_CHUNKED_RESPONSE), ServerDirection.RESPONSE, new ByteArrayOutputStream()))
                .isInstanceOf(BodyDecodeException.class)
                .hasMessageContaining("chunk-size");
    }

    @Test
    void writeFailureIsNotMistakenForUndecodableBody()
    {
        final OutputStream failing = new OutputStream()
        {
            @Override
            public void write(final int b) throws IOException
            {
                throw new IOException("No space left on device");
            }

            @Override
            public void write(final byte[] b, final int off, final int len) throws IOException
            {
                throw new IOException("No space left on device");
            }
        };

        assertThatThrownBy(() -> HttpBodyUtil.writeBody(stream(CHUNKED_RESPONSE), ServerDirection.RESPONSE, failing))
                .isNotInstanceOf(BodyDecodeException.class)
                .hasMessage("No space left on device");
    }

    private static InputStream stream(final String message)
    {
        return new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8));
    }
}
