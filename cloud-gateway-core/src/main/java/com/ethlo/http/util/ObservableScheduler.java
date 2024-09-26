package com.ethlo.http.util;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.Nonnull;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

public class ObservableScheduler implements Scheduler
{
    private final Scheduler delegate;
    private final BlockingQueue<Runnable> queue;
    private final int queueSize;
    private final AtomicInteger rejectedDelayCount;
    private final AtomicLong rejectedDelay;

    public ObservableScheduler(Scheduler delegate, BlockingQueue<Runnable> queue, final int queueSize, final AtomicInteger rejectedDelayCount, final AtomicLong rejectedDelay)
    {
        this.delegate = delegate;
        this.queue = queue;
        this.queueSize = queueSize;
        this.rejectedDelayCount = rejectedDelayCount;
        this.rejectedDelay = rejectedDelay;
    }

    @Override
    @Nonnull
    public Disposable schedule(@Nonnull final Runnable task)
    {
        return delegate.schedule(task);
    }

    @Override
    @Nonnull
    public Worker createWorker()
    {
        return delegate.createWorker();
    }

    public int getQueueSize()
    {
        return queue.size();
    }

    public int getQueueCapacity()
    {
        return queueSize;
    }

    public int getQueueInsertDelayCount()
    {
        return rejectedDelayCount.get();
    }

    public Duration getQueueInsertDelayElapsed()
    {
        return Duration.ofNanos(rejectedDelay.get());
    }
}