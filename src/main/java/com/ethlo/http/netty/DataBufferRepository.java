package com.ethlo.http.netty;

import java.util.Optional;

import com.ethlo.http.model.PayloadProvider;

public interface DataBufferRepository
{
    void cleanup(String requestId);

    void save(final ServerDirection operation, final String id, byte[] data);

    void finished(String requestId);

    Optional<PayloadProvider> get(final ServerDirection operation, final String id);

}
