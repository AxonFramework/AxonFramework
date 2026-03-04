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

import org.axonframework.common.FutureUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.jdbc.JdbcUtils;
import org.axonframework.common.tx.TransactionalExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static org.axonframework.common.jdbc.JdbcUtils.executeQuery;
import static org.axonframework.common.jdbc.JdbcUtils.listResults;

/**
 * Enables iterating through a JDBC query using paging. Paging is taken care of automatically through the provided
 * {@link PagingStatementSupplier}, fetching the next page when the items run out to iterate through.
 * <p>
 * Do not use this for paging when you care about concurrent deletes. If you loaded a page, delete an item from it, and
 * load the next, you will miss an item during iteration.
 * <p>
 * The {@link #iterator()} function can be called multiple times to loop through the items, restarting the query from
 * the start.
 *
 * @param <R> The mapped result type.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 4.8.0
 */
@Internal
public class PagingJdbcIterable<R> implements Iterable<R> {

    private static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(30);

    private final int pageSize;
    private final Duration queryTimeout;
    private final TransactionalExecutor<Connection> executor;
    private final PagingStatementSupplier pagingQuerySupplier;
    private final JdbcUtils.SqlResultConverter<R> resultConverter;
    private final Function<SQLException, RuntimeException> errorHandler;

    /**
     * Construct a new {@link Iterable} of type {@code R}, utilizing paging queries to retrieve the entries.
     * <p>
     * Uses a default query timeout of 30 seconds per page fetch.
     *
     * @param executor            The {@link TransactionalExecutor} used to execute the paging query.
     * @param pagingQuerySupplier A factory function supply the paging {@link PreparedStatement} to execute.
     * @param pageSize            The size of the pages to retrieve. Used to calculate the {@code offset} and
     *                            {@code maxSize} of the paging query
     * @param resultConverter     The converter of the {@link java.sql.ResultSet} into entries of type {@code R}.
     * @param errorHandler        The error handler to deal with exceptions when executing a paging
     *                            {@link PreparedStatement}.
     */
    public PagingJdbcIterable(TransactionalExecutor<Connection> executor,
                              PagingStatementSupplier pagingQuerySupplier,
                              int pageSize,
                              JdbcUtils.SqlResultConverter<R> resultConverter,
                              Function<SQLException, RuntimeException> errorHandler) {
        this(executor, pagingQuerySupplier, pageSize, DEFAULT_QUERY_TIMEOUT, resultConverter, errorHandler);
    }

    /**
     * Construct a new {@link Iterable} of type {@code R}, utilizing paging queries to retrieve the entries.
     *
     * @param executor            The {@link TransactionalExecutor} used to execute the paging query.
     * @param pagingQuerySupplier A factory function supply the paging {@link PreparedStatement} to execute.
     * @param pageSize            The size of the pages to retrieve. Used to calculate the {@code offset} and
     *                            {@code maxSize} of the paging query
     * @param queryTimeout        The maximum time to wait for each page query to complete.
     * @param resultConverter     The converter of the {@link java.sql.ResultSet} into entries of type {@code R}.
     * @param errorHandler        The error handler to deal with exceptions when executing a paging
     *                            {@link PreparedStatement}.
     */
    public PagingJdbcIterable(TransactionalExecutor<Connection> executor,
                              PagingStatementSupplier pagingQuerySupplier,
                              int pageSize,
                              Duration queryTimeout,
                              JdbcUtils.SqlResultConverter<R> resultConverter,
                              Function<SQLException, RuntimeException> errorHandler) {
        this.executor = executor;
        this.pagingQuerySupplier = pagingQuerySupplier;
        this.pageSize = pageSize;
        this.queryTimeout = queryTimeout;
        this.resultConverter = resultConverter;
        this.errorHandler = errorHandler;
    }

    /**
     * The {@link Iterator} that loops through the provided query's pages until it runs out of items.
     */
    public class PagingIterator implements Iterator<R> {

        private Deque<R> currentPage = new ArrayDeque<>();
        private int page = 0;

        @Override
        public boolean hasNext() {
            refreshPageIfNecessary();
            return !currentPage.isEmpty();
        }

        @Override
        public R next() {
            refreshPageIfNecessary();
            R row = currentPage.pop();
            if (row == null) {
                throw new NoSuchElementException();
            }
            return row;
        }

        private void refreshPageIfNecessary() {
            if (!currentPage.isEmpty()) {
                return;
            }

            FutureUtils.joinAndUnwrap(
                    executor.accept(connection -> {
                        var results = executeQuery(connection,
                                                   c -> pagingQuerySupplier.apply(c, page * pageSize, pageSize),
                                                   listResults(resultConverter),
                                                   errorHandler,
                                                   false);
                        currentPage = new ArrayDeque<>(results);
                    }),
                    queryTimeout
            );
            page++;
        }
    }

    @Override
    public Iterator<R> iterator() {
        return new PagingIterator();
    }

    /**
     * Describes a function that creates a new {@link PreparedStatement} that pages results through the given
     * {@code offset} and {@code maxSize}, ready to be executed.
     */
    @FunctionalInterface
    public interface PagingStatementSupplier {

        /**
         * Create a new {@link PreparedStatement} using the given {@code connection}, paging through the provided
         * {@code offset} and {@code maxSize}.
         *
         * @param connection The connection that will be used to create the {@link PreparedStatement}.
         * @param offset     The offset from where to start the page.
         * @param maxSize    The maximum size of the page, resulting in the maximum page size.
         * @return A paging {@link PreparedStatement} ready for execution.
         * @throws SQLException When the statement could not be created.
         */
        PreparedStatement apply(Connection connection, int offset, int maxSize) throws SQLException;
    }
}
