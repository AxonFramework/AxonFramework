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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.axonframework.common.jdbc.JdbcUtils.*;

/**
 * Enables iterating through a JPA query using paging while lazily mapping the results when necessary. Paging is taken
 * care of automatically, fetching the next page when the items run out to iterate through.
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
    private final PagingSqlFunction sqlBuilder;
    private final JdbcUtils.SqlResultConverter<R> resultConverter;
    private final Function<SQLException, RuntimeException> errorHandler;
    private final boolean closeConnection;

    /**
     * Constructs a new {@link Iterable} using the provided {@code querySupplier} to construct queries when a new page
     * needs to be fetched. Items are lazily mapped by the provided {@code resultConverter} when iterating.
     *
     * @param closeConnection
     * @param pageSize           The size of the pages.
     * @param transactionManager The {@link TransactionManager} to use when fetching items.
     * @param resultConverter    The mapping function to map items to the desired representation.
     */
    public PagingJdbcIterable(int pageSize,
                              Supplier<Connection> connectionProvider,
                              TransactionManager transactionManager,
                              PagingSqlFunction sqlBuilder,
                              JdbcUtils.SqlResultConverter<R> resultConverter,
                              Function<SQLException, RuntimeException> errorHandler,
                              boolean closeConnection
    ) {
        this.pageSize = pageSize;
        this.connectionProvider = connectionProvider;
        this.transactionManager = transactionManager;
        this.sqlBuilder = sqlBuilder;
        this.resultConverter = resultConverter;
        this.errorHandler = errorHandler;
        this.closeConnection = closeConnection;
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
                                       connection -> sqlBuilder.apply(connection, page * pageSize, pageSize),
                                       listResults(resultConverter),
                                       errorHandler,
                                       closeConnection)
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
     * Describes a function that creates a new {@link PreparedStatement} that pages results based on the
     * {@code firstResult} and {@code maxSize}, ready to be executed.
     */
    @FunctionalInterface
    public interface PagingSqlFunction {

        /**
         * Create a new {@link PreparedStatement} using the given {@code connection}.
         * todo jdoc
         * @param connection the connection that will be used to create the statement
         * @return a new statement ready for execution
         * @throws SQLException if the statement could not be created
         */
        PreparedStatement apply(Connection connection, int offset, int maxSize) throws SQLException;
    }
}
