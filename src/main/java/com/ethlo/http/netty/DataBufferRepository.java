package com.ethlo.http.netty;

import java.io.OutputStream;
import java.util.Optional;

import com.ethlo.http.model.PayloadProvider;

public interface DataBufferRepository
{
    void cleanup(String requestId);

    void write(final ServerDirection operation, final String id, byte[] data);

    OutputStream getOutputStream(final ServerDirection operation, final String id);

    void finished(String requestId);

    Optional<PayloadProvider> get(final ServerDirection serverDirection, final String id);

}
