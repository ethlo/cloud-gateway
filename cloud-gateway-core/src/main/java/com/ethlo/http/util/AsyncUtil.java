package com.ethlo.http.util;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AsyncUtil
{
    private AsyncUtil()
    {

    }

    public static <T> CompletableFuture<List<T>> join(List<CompletableFuture<T>> executionPromises)
    {
        final CompletableFuture<Void> joinedPromise = CompletableFuture.allOf(executionPromises.toArray(CompletableFuture[]::new));
        return joinedPromise.thenApply(it -> executionPromises
                .stream()
                .map(CompletableFuture::join)
                .toList());
    }
}
