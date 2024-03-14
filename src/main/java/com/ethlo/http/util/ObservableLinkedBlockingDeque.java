package com.ethlo.http.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.annotation.Nonnull;

public class ObservableLinkedBlockingDeque<T> implements BlockingQueue<T>
{
    private final BlockingQueue<T> delegate;

    public ObservableLinkedBlockingDeque(int queueSize)
    {
        delegate = new LinkedBlockingDeque<>(queueSize);
    }

    @Override
    public boolean add(@Nonnull final T t)
    {
        return delegate.add(t);
    }

    @Override
    public boolean offer(@Nonnull final T t)
    {
        return delegate.offer(t);
    }

    @Override
    public void put(@Nonnull final T t) throws InterruptedException
    {
        delegate.put(t);
    }

    @Override
    public boolean offer(final T t, final long timeout, @Nonnull final TimeUnit unit) throws InterruptedException
    {
        return delegate.offer(t, timeout, unit);
    }

    @Nonnull
    @Override
    public T take() throws InterruptedException
    {
        return delegate.take();
    }

    @Override
    public T poll(final long timeout, @Nonnull final TimeUnit unit) throws InterruptedException
    {
        return delegate.poll(timeout, unit);
    }

    @Override
    public int remainingCapacity()
    {
        return delegate.remainingCapacity();
    }

    @Override
    public boolean remove(final Object o)
    {
        return delegate.remove(o);
    }

    @Override
    public boolean contains(final Object o)
    {
        return delegate.contains(o);
    }

    @Override
    public int drainTo(@Nonnull final Collection<? super T> c)
    {
        return delegate.drainTo(c);
    }

    @Override
    public int drainTo(@Nonnull final Collection<? super T> c, final int maxElements)
    {
        return delegate.drainTo(c, maxElements);
    }

    @Override
    public T remove()
    {
        return delegate.remove();
    }

    @Override
    public T poll()
    {
        return delegate.poll();
    }

    @Override
    public T element()
    {
        return delegate.element();
    }

    @Override
    public T peek()
    {
        return delegate.peek();
    }

    @Override
    public int size()
    {
        return delegate.size();
    }

    @Override
    public boolean isEmpty()
    {
        return delegate.isEmpty();
    }

    @Nonnull
    @Override
    public Iterator<T> iterator()
    {
        return delegate.iterator();
    }

    @Nonnull
    @Override
    public Object[] toArray()
    {
        return delegate.toArray();
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull final T1[] a)
    {
        return delegate.toArray(a);
    }

    @Override
    public <T1> T1[] toArray(final IntFunction<T1[]> generator)
    {
        return delegate.toArray(generator);
    }

    @Override
    public boolean containsAll(@Nonnull final Collection<?> c)
    {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(@Nonnull final Collection<? extends T> c)
    {
        return delegate.addAll(c);
    }

    @Override
    public boolean removeAll(@Nonnull final Collection<?> c)
    {
        return delegate.removeAll(c);
    }

    @Override
    public boolean removeIf(final Predicate<? super T> filter)
    {
        return delegate.removeIf(filter);
    }

    @Override
    public boolean retainAll(@Nonnull final Collection<?> c)
    {
        return delegate.retainAll(c);
    }

    @Override
    public void clear()
    {
        delegate.clear();
    }

    @Override
    public Spliterator<T> spliterator()
    {
        return delegate.spliterator();
    }

    @Override
    public Stream<T> stream()
    {
        return delegate.stream();
    }

    @Override
    public Stream<T> parallelStream()
    {
        return delegate.parallelStream();
    }

    @Override
    public void forEach(final Consumer<? super T> action)
    {
        delegate.forEach(action);
    }
}
