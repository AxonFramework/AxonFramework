/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.common.jdbc;

import org.axonframework.common.transaction.TransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

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
public class PagingJdbcIterable<R> implements Iterable<R> {

    private final int pageSize;
    private final Supplier<Connection> connectionProvider;
    private final TransactionManager transactionManager;
    private final PagingStatementSupplier pagingQuerySupplier;
    private final JdbcUtils.SqlResultConverter<R> resultConverter;
    private final Function<SQLException, RuntimeException> errorHandler;

    /**
     * Construct a new {@link Iterable} of type {@code R}, utilizing paging queries to retrieve the entries.
     *
     * @param transactionManager  The {@link TransactionManager} used to execute the paging query.
     * @param connectionProvider  The supplier of the {@link Connection} used by the given {@code pagingQuerySupplier}.
     * @param pagingQuerySupplier A factory function supply the paging {@link PreparedStatement} to execute.
     * @param pageSize            The size of the pages to retrieve. Used to calculate the {@code offset} and
     *                            {@code maxSize} of the paging query
     * @param resultConverter     The converter of the {@link java.sql.ResultSet} into entries of type {@code R}.
     * @param errorHandler        The error handler to deal with exceptions when executing a paging
     *                            {@link PreparedStatement}.
     */
    public PagingJdbcIterable(TransactionManager transactionManager,
                              Supplier<Connection> connectionProvider,
                              PagingStatementSupplier pagingQuerySupplier,
                              int pageSize,
                              JdbcUtils.SqlResultConverter<R> resultConverter,
                              Function<SQLException, RuntimeException> errorHandler) {
        this.transactionManager = transactionManager;
        this.connectionProvider = connectionProvider;
        this.pagingQuerySupplier = pagingQuerySupplier;
        this.pageSize = pageSize;
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

            List<R> results = transactionManager.fetchInTransaction(
                    () -> executeQuery(connectionProvider.get(),
                                       connection -> pagingQuerySupplier.apply(connection, page * pageSize, pageSize),
                                       listResults(resultConverter),
                                       errorHandler)
            );
            currentPage = new ArrayDeque<>(results);
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
