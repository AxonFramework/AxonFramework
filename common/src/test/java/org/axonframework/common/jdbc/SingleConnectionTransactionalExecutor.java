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

package org.axonframework.common.jdbc;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.function.ThrowingFunction;
import org.axonframework.common.tx.TransactionalExecutor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A test-only {@link TransactionalExecutor} that reuses a single JDBC {@link Connection} with proper transaction
 * management. Unlike the production executor in
 * {@link org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider
 * JdbcTransactionalExecutorProvider}, this executor never closes the connection, making it suitable for HSQLDB
 * in-memory databases where closing the last connection destroys the database.
 * <p>
 * Each invocation sets {@code autoCommit(false)}, executes the function, and either commits or rolls back. The
 * connection remains open for reuse across operations.
 * <p>
 * This is the JDBC analog of using {@code EntityManagerExecutor(() -> entityManager)} in JPA tests.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class SingleConnectionTransactionalExecutor implements TransactionalExecutor<Connection> {

    private final Connection connection;

    /**
     * Creates a new instance that reuses the given {@link Connection} for all operations.
     *
     * @param connection The JDBC {@link Connection} to reuse, cannot be {@code null}.
     */
    public SingleConnectionTransactionalExecutor(@Nonnull Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    @Nonnull
    public <R> CompletableFuture<R> apply(@Nonnull ThrowingFunction<Connection, R, Exception> function) {
        Objects.requireNonNull(function, "function");
        try {
            connection.setAutoCommit(false);
            R result = function.apply(connection);
            connection.commit();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException se) {
                e.addSuppressed(se);
            }
            return CompletableFuture.failedFuture(e);
        }
    }
}
