package com.ethlo.http.netty;

import java.math.BigInteger;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import jakarta.annotation.Nonnull;

public class RequestIdWrapper extends ServerHttpRequestDecorator
{
    private final String requestId;

    public RequestIdWrapper(ServerHttpRequest origRequest, final String requestId)
    {
        super(origRequest);
        this.requestId = requestId;
    }

    public static String generateId()
    {
        final String timestampPart = Long.toString(Instant.now().toEpochMilli(), 36);
        final String randomPart = new BigInteger(56, ThreadLocalRandom.current()).toString(36);
        return timestampPart + "-" + randomPart;
    }

    @Override
    @Nonnull
    public String getId()
    {
        return requestId;
    }
}
