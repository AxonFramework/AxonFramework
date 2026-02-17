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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.tx.TransactionalExecutor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A paging {@link Flow.Publisher} backed by JPA queries.
 *
 * @param <T> The query result type.
 * @param <R> The mapped result type.
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class PagingJpaQueryPublisher<T, R> implements Flow.Publisher<R> {

    private final int pageSize;
    private final Executor streamExecutor;
    private final TransactionalExecutor<EntityManager> executor;
    private final Function<EntityManager, TypedQuery<T>> queryFunction;
    private final Function<T, R> mappingFunction;

    /**
     * Constructs a paging publisher using the given query and mapping functions.
     *
     * @param pageSize        Size of each page to fetch.
     * @param streamExecutor  Executor used to run emission loops.
     * @param executor        Transactional executor used for JPA queries.
     * @param queryFunction   Function creating the typed query for each page.
     * @param mappingFunction Function mapping query rows to output values.
     */
    public PagingJpaQueryPublisher(int pageSize,
                                   @Nonnull Executor streamExecutor,
                                   @Nonnull TransactionalExecutor<EntityManager> executor,
                                   @Nonnull Function<EntityManager, TypedQuery<T>> queryFunction,
                                   @Nonnull Function<T, R> mappingFunction) {
        this.pageSize = pageSize;
        this.streamExecutor = Objects.requireNonNull(streamExecutor, "streamExecutor");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.queryFunction = Objects.requireNonNull(queryFunction, "queryFunction");
        this.mappingFunction = Objects.requireNonNull(mappingFunction, "mappingFunction");
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        PagingSubscription subscription = new PagingSubscription(subscriber);
        subscriber.onSubscribe(subscription);
    }

    private class PagingSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super R> subscriber;
        private final AtomicLong requested = new AtomicLong();
        private final AtomicInteger workInProgress = new AtomicInteger();
        private final Deque<T> queue = new ArrayDeque<>();

        private volatile boolean cancelled;
        private volatile boolean completed;
        private int page = 0;

        private PagingSubscription(Flow.Subscriber<? super R> subscriber) {
            this.subscriber = subscriber;
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
            streamExecutor.execute(this::drain);
        }

        private void drain() {
            int missed = 1;
            while (!cancelled && !completed) {
                long demand = requested.get();
                long emitted = 0L;

                while (emitted < demand && !cancelled && !completed) {
                    if (queue.isEmpty()) {
                        List<T> fetchedRows;
                        try {
                            fetchedRows = executor.apply(em -> queryFunction.apply(em)
                                                                        .setMaxResults(pageSize)
                                                                        .setFirstResult(page * pageSize)
                                                                        .getResultList())
                                                .join();
                        } catch (CompletionException e) {
                            fail(FutureUtils.unwrap(e));
                            return;
                        } catch (Exception e) {
                            fail(e);
                            return;
                        }

                        page++;
                        if (fetchedRows.isEmpty()) {
                            completed = true;
                            subscriber.onComplete();
                            return;
                        }
                        fetchedRows.forEach(queue::offerLast);
                    }

                    T next = queue.pollFirst();
                    if (next == null) {
                        continue;
                    }
                    R mapped;
                    try {
                        mapped = mappingFunction.apply(next);
                    } catch (Exception e) {
                        fail(e);
                        return;
                    }
                    try {
                        subscriber.onNext(mapped);
                    } catch (Exception e) {
                        fail(e);
                        return;
                    }
                    emitted++;
                }

                if (emitted > 0) {
                    requested.addAndGet(-emitted);
                }

                missed = workInProgress.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

        private void fail(Throwable e) {
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
