package com.ethlo.http.netty;

import java.io.BufferedInputStream;

public interface DataBufferRepository
{
    void cleanup(String requestId);

    void save(final Operation operation, final String id, byte[] data);

    void finished(String requestId);

    BufferedInputStream get(final Operation operation, final String id);

    enum Operation
    {
        REQUEST, RESPONSE;
    }
}
