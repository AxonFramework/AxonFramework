/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.deadletter;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Flow.Publisher} that publishes items from an {@link Iterable}.
 * <p>
 * Demand is respected through {@link Flow.Subscription#request(long)} and items are emitted in sequence.
 *
 * @param <T> Type of element emitted by this publisher.
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class IterablePublisher<T> implements Flow.Publisher<T> {

    private final Iterable<? extends T> iterable;
    private final Executor executor;

    /**
     * Creates an {@link IterablePublisher} running drain operations on the caller thread.
     *
     * @param iterable Source iterable.
     */
    public IterablePublisher(@Nonnull Iterable<? extends T> iterable) {
        this(iterable, Runnable::run);
    }

    /**
     * Creates an {@link IterablePublisher} using the given {@code executor} for drain operations.
     *
     * @param iterable Source iterable.
     * @param executor Executor used to execute emission drainer.
     */
    public IterablePublisher(@Nonnull Iterable<? extends T> iterable, @Nonnull Executor executor) {
        this.iterable = Objects.requireNonNull(iterable, "iterable");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");

        Iterator<? extends T> iterator;
        try {
            iterator = iterable.iterator();
        } catch (Exception e) {
            new FailedPublisher<T>(e).subscribe(subscriber);
            return;
        }

        IterableSubscription subscription = new IterableSubscription(subscriber, iterator, executor);
        subscriber.onSubscribe(subscription);
    }

    private static final class IterableSubscription<T> implements Flow.Subscription {

        private final Flow.Subscriber<? super T> subscriber;
        private final Iterator<? extends T> iterator;
        private final Executor executor;
        private final AtomicLong requested = new AtomicLong();
        private final AtomicInteger workInProgress = new AtomicInteger();

        private volatile boolean cancelled;
        private volatile boolean completed;

        private IterableSubscription(Flow.Subscriber<? super T> subscriber,
                                     Iterator<? extends T> iterator,
                                     Executor executor) {
            this.subscriber = subscriber;
            this.iterator = iterator;
            this.executor = executor;
        }

        @Override
        public void request(long n) {
            if (cancelled || completed) {
                return;
            }
            if (n <= 0) {
                cancel();
                subscriber.onError(new IllegalArgumentException("request must be greater than zero"));
                return;
            }
            addRequested(n);
            schedule();
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        private void schedule() {
            if (workInProgress.getAndIncrement() != 0) {
                return;
            }
            executor.execute(this::drain);
        }

        private void drain() {
            int missed = 1;
            while (!cancelled && !completed) {
                long demand = requested.get();
                long emitted = 0L;
                while (emitted < demand && !cancelled && !completed) {
                    boolean hasNext;
                    try {
                        hasNext = iterator.hasNext();
                    } catch (Exception e) {
                        fail(e);
                        return;
                    }
                    if (!hasNext) {
                        completed = true;
                        subscriber.onComplete();
                        return;
                    }

                    T next;
                    try {
                        @SuppressWarnings("unchecked")
                        T item = (T) iterator.next();
                        next = item;
                    } catch (Exception e) {
                        fail(e);
                        return;
                    }
                    subscriber.onNext(next);
                    emitted++;
                }

                if (emitted != 0) {
                    requested.addAndGet(-emitted);
                }

                missed = workInProgress.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

        private void fail(Exception e) {
            cancelled = true;
            completed = true;
            subscriber.onError(e);
        }

        private void addRequested(long n) {
            long previous;
            long updated;
            do {
                previous = requested.get();
                if (previous == Long.MAX_VALUE) {
                    return;
                }
                updated = previous + n;
                if (updated < 0L) {
                    updated = Long.MAX_VALUE;
                }
            } while (!requested.compareAndSet(previous, updated));
        }
    }
}

