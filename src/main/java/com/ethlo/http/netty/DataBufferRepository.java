package com.ethlo.http.netty;

import com.ethlo.http.model.PayloadProvider;

import java.util.Optional;

public interface DataBufferRepository
{
    void cleanup(String requestId);

    void save(final Operation operation, final String id, byte[] data);

    void finished(String requestId);

    Optional<PayloadProvider> get(final Operation operation, final String id);

    enum Operation
    {
        REQUEST, RESPONSE;
    }
}
