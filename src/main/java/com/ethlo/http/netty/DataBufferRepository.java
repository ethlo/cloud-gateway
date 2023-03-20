package com.ethlo.http.netty;

import java.io.BufferedInputStream;
import java.io.InputStream;

public interface DataBufferRepository
{
    void cleanup(String requestId);

    enum Operation
    {
        REQUEST, RESPONSE;
    }

    void save(final Operation operation, final String id, byte[] data);

    void finished(String requestId);

    BufferedInputStream get(final Operation operation, final String id);
}
