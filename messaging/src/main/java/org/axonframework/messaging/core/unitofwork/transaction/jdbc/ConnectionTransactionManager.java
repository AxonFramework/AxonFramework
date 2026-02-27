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

package org.axonframework.messaging.core.unitofwork.transaction.jdbc;

import jakarta.annotation.Nonnull;
import org.axonframework.common.jdbc.ConnectionExecutor;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.common.jdbc.JdbcUtils;
import org.axonframework.conversion.CachingSupplier;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.core.unitofwork.transaction.Transaction;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A {@link TransactionManager} implementation that manages JDBC {@link Connection} transactions
 * directly via a {@link ConnectionProvider}.
 * <p>
 * This implementation registers a {@link ConnectionExecutor} supplier into the {@link
 * org.axonframework.messaging.core.unitofwork.ProcessingContext} using {@link #SUPPLIER_KEY},
 * making the connection available to components that consume it (such as
 * {@link JdbcTransactionalExecutorProvider}).
 * <p>
 * This is the canonical owner of {@link #SUPPLIER_KEY} for JDBC-based resources in a processing
 * context, allowing both pure-JDBC and Spring-managed transaction scenarios to share the same
 * resource key.
 * <p>
 * Transaction propagation follows a {@code PROPAGATION_REQUIRED} semantic: if the obtained
 * {@link Connection} already has {@code autoCommit=false}, the existing transaction is joined
 * without taking ownership of its lifecycle (commit and rollback are no-ops).
 * <p>
 * <strong>Single-connection guarantee:</strong> Because {@link ConnectionProvider#getConnection()}
 * typically returns a new physical connection on each invocation,
 * {@link #attachToProcessingLifecycle(ProcessingLifecycle)} obtains <em>one</em> connection and
 * captures it so that the registered {@link ConnectionExecutor} always operates on the same
 * connection that the transaction was started on.
 * <p>
 * Example usage:
 * <pre>{@code
 * ConnectionProvider provider = dataSource::getConnection;
 * TransactionManager txManager = new ConnectionTransactionManager(provider);
 * txManager.attachToProcessingLifecycle(processingLifecycle);
 * }</pre>
 *
 * @author Axon Framework Contributors
 * @since 5.1.0
 */
public class ConnectionTransactionManager implements TransactionManager {

    /**
     * Canonical resource key for the {@link ConnectionExecutor} supplier stored in a
     * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}.
     * <p>
     * This key is the single authoritative definition shared by this class and
     * {@link JdbcTransactionalExecutorProvider}.
     */
    public static final ResourceKey<Supplier<ConnectionExecutor>> SUPPLIER_KEY =
            ResourceKey.withLabel(ConnectionExecutor.class.getSimpleName());

    private final ConnectionProvider connectionProvider;

    /**
     * Constructs a new {@code ConnectionTransactionManager}.
     *
     * @param connectionProvider the provider of the {@link Connection} to use, cannot be
     *                           {@code null}
     * @throws NullPointerException if {@code connectionProvider} is {@code null}
     */
    public ConnectionTransactionManager(@Nonnull ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Obtains a {@link Connection} from the provider and manages its transaction lifecycle. If the
     * connection already has {@code autoCommit=false}, the existing transaction is joined and the
     * returned {@link Transaction}'s commit and rollback are no-ops. Otherwise a new transaction is
     * started by setting {@code autoCommit=false}, and the returned {@link Transaction} takes
     * ownership of commit, rollback, and connection closing.
     *
     * @throws JdbcException if obtaining or configuring the connection fails
     */
    @Nonnull
    @Override
    public Transaction startTransaction() {
        Connection connection;
        try {
            connection = connectionProvider.getConnection();
        } catch (SQLException e) {
            throw new JdbcException("Failed to obtain a connection", e);
        }
        boolean newTransaction;
        try {
            newTransaction = connection.getAutoCommit();
            if (newTransaction) {
                connection.setAutoCommit(false);
            }
        } catch (SQLException e) {
            JdbcUtils.closeQuietly(connection);
            throw new JdbcException("Failed to configure transaction on connection", e);
        }
        return new Transaction() {
            @Override
            public void commit() {
                try {
                    if (newTransaction) {
                        connection.commit();
                    }
                } catch (SQLException e) {
                    throw new JdbcException("Failed to commit transaction", e);
                } finally {
                    JdbcUtils.closeQuietly(connection);
                }
            }

            @Override
            public void rollback() {
                try {
                    if (newTransaction) {
                        connection.rollback();
                    }
                } catch (SQLException e) {
                    // suppress — rollback failure should not mask the original error
                } finally {
                    JdbcUtils.closeQuietly(connection);
                }
            }
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * Obtains a single {@link Connection} from the provider and registers a
     * {@link CachingSupplier}-wrapped {@link ConnectionExecutor} under {@link #SUPPLIER_KEY} in the
     * processing context. The executor always uses the <em>same</em> connection that the transaction
     * was started on — ensuring all work within the processing context participates in the same
     * JDBC transaction.
     * <p>
     * In addition to the standard transaction lifecycle (start → commit/rollback), this method
     * closes the connection after commit or rollback.
     *
     * @throws JdbcException if obtaining or configuring the connection fails
     */
    @Override
    public void attachToProcessingLifecycle(@Nonnull ProcessingLifecycle processingLifecycle) {
        processingLifecycle.runOnPreInvocation(pc -> {
            Connection connection;
            try {
                connection = connectionProvider.getConnection();
            } catch (SQLException e) {
                throw new JdbcException("Failed to obtain a connection", e);
            }
            boolean newTransaction;
            try {
                newTransaction = connection.getAutoCommit();
                if (newTransaction) {
                    connection.setAutoCommit(false);
                }
            } catch (SQLException e) {
                JdbcUtils.closeQuietly(connection);
                throw new JdbcException("Failed to configure transaction on connection", e);
            }
            // Capture the specific connection so all work within this processing context
            // uses the same Connection that the transaction was started on.
            pc.putResource(SUPPLIER_KEY, CachingSupplier.of(() -> new ConnectionExecutor(() -> connection)));
            pc.runOnCommit(p -> {
                try {
                    if (newTransaction) {
                        connection.commit();
                    }
                } catch (SQLException e) {
                    throw new JdbcException("Failed to commit transaction", e);
                } finally {
                    JdbcUtils.closeQuietly(connection);
                }
            });
            pc.onError((p, phase, e) -> {
                try {
                    if (newTransaction) {
                        connection.rollback();
                    }
                } catch (SQLException se) {
                    e.addSuppressed(se);
                } finally {
                    JdbcUtils.closeQuietly(connection);
                }
            });
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code true} because JDBC {@link Connection} objects are not thread-safe and must be
     * accessed from the thread that initiated the transaction.
     */
    @Override
    public boolean requiresSameThreadInvocations() {
        return true;
    }
}
