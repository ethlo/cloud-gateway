package com.ethlo.http.netty;

import java.io.InputStream;

public interface DataBufferRepository
{
    enum Operation
    {
        REQUEST, RESPONSE;
    }

    void save(final Operation operation, final String id, byte[] data);

    void finished(String requestId);

    InputStream get(final Operation operation, final String id);
}
