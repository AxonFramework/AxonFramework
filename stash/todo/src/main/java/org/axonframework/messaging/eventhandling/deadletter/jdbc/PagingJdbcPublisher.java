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

package org.axonframework.messaging.eventhandling.deadletter.jdbc;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.jdbc.JdbcUtils;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.axonframework.common.jdbc.JdbcUtils.executeQuery;
import static org.axonframework.common.jdbc.JdbcUtils.listResults;

/**
 * A paging {@link Flow.Publisher} backed by JDBC queries.
 *
 * @param <R> The mapped result type.
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class PagingJdbcPublisher<R> implements Flow.Publisher<R> {

    private final Executor streamExecutor;
    private final Supplier<Connection> connectionProvider;
    private final TransactionManager transactionManager;
    private final PagingStatementSupplier pagingQuerySupplier;
    private final int pageSize;
    private final JdbcUtils.SqlResultConverter<R> resultConverter;
    private final Function<SQLException, RuntimeException> errorHandler;

    /**
     * Construct a new paging publisher of type {@code R}, utilizing paging queries to retrieve the entries.
     *
     * @param streamExecutor    Executor used to run drain operations.
     * @param transactionManager The {@link TransactionManager} used to execute the paging query.
     * @param connectionProvider The supplier of the {@link Connection} used by the given {@code pagingQuerySupplier}.
     * @param pagingQuerySupplier A factory function supply the paging statement to execute.
     * @param pageSize The size of the pages to retrieve.
     * @param resultConverter The converter of the {@link java.sql.ResultSet} into entries of type {@code R}.
     * @param errorHandler The error handler to deal with exceptions when executing a paging statement.
     */
    public PagingJdbcPublisher(@Nonnull Executor streamExecutor,
                               @Nonnull TransactionManager transactionManager,
                               @Nonnull Supplier<Connection> connectionProvider,
                               @Nonnull PagingStatementSupplier pagingQuerySupplier,
                               int pageSize,
                               @Nonnull JdbcUtils.SqlResultConverter<R> resultConverter,
                               @Nonnull Function<SQLException, RuntimeException> errorHandler) {
        this.streamExecutor = Objects.requireNonNull(streamExecutor, "streamExecutor");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.pagingQuerySupplier = Objects.requireNonNull(pagingQuerySupplier, "pagingQuerySupplier");
        this.pageSize = pageSize;
        this.resultConverter = Objects.requireNonNull(resultConverter, "resultConverter");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    /**
     * Describes a function that creates a new paging statement through the given {@code offset} and {@code maxSize}.
     */
    @FunctionalInterface
    public interface PagingStatementSupplier {

        /**
         * Create a new paging statement using the given {@code connection}, paging through the provided
         * {@code offset} and {@code maxSize}.
         *
         * @param connection The connection that will be used to create the statement.
         * @param offset The offset from where to start the page.
         * @param maxSize The maximum size of the page.
         * @return A paging statement ready for execution.
         * @throws SQLException When the statement could not be created.
         */
        java.sql.PreparedStatement apply(Connection connection, int offset, int maxSize) throws SQLException;
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
        private final Deque<R> queue = new ArrayDeque<>();

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
                        try {
                            List<R> results = transactionManager.fetchInTransaction(
                                    () -> executeQuery(connectionProvider.get(),
                                                       connection -> pagingQuerySupplier.apply(connection,
                                                                                               page * pageSize,
                                                                                               pageSize),
                                                       listResults(resultConverter),
                                                       errorHandler)
                            );
                            page++;
                            if (results.isEmpty()) {
                                completed = true;
                                subscriber.onComplete();
                                return;
                            }
                            queue.addAll(results);
                        } catch (Exception e) {
                            fail(e);
                            return;
                        }
                    }

                    R next = queue.pollFirst();
                    if (next == null) {
                        continue;
                    }
                    try {
                        subscriber.onNext(next);
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
