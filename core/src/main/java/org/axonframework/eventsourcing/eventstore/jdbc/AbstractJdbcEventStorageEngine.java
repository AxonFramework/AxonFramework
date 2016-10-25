/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcasterChain;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.common.io.IOUtils.closeQuietly;

/**
 * Abstract implementation of an event storage engine that uses JDBC to store and load events.
 *
 * @author Rene de Waele
 */
public abstract class AbstractJdbcEventStorageEngine extends BatchingEventStorageEngine {
    private final ConnectionProvider connectionProvider;

    /**
     * Initializes an EventStorageEngine that uses JDBC to store and load events.
     *
     * @param serializer                   Used to serialize and deserialize event payload and metadata. If {@code null}
     *                                     an {@link XStreamSerializer} is used.
     * @param upcasterChain                Allows older revisions of serialized objects to be deserialized. If {@code
     *                                     null} a {@link NoOpEventUpcasterChain} is used.
     * @param persistenceExceptionResolver Detects concurrency exceptions from the backing database. If {@code null}, a
     *                                     .
     * @param batchSize                    The number of events that should be read at each database access. When more
     *                                     than this number of events must be read to rebuild an aggregate's state, the
     *                                     events are read in batches of this size. If {@code null} a batch size of 100
     *                                     is used. Tip: if you use a snapshotter, make sure to choose snapshot trigger
     *                                     and batch size such that a single batch will generally retrieve all events
     *                                     required to rebuild an aggregate's state.
     * @param connectionProvider           The provider of connections to the underlying database.
     */
    protected AbstractJdbcEventStorageEngine(Serializer serializer, EventUpcasterChain upcasterChain,
                                             PersistenceExceptionResolver persistenceExceptionResolver,
                                             Integer batchSize, ConnectionProvider connectionProvider) {
        super(serializer, upcasterChain, getOrDefault(persistenceExceptionResolver, new JdbcSQLErrorCodesResolver()),
              batchSize);
        this.connectionProvider = connectionProvider;
    }

    /**
     * Returns a {@link Connection} to the database.
     *
     * @return a database Connection
     */
    protected Connection getConnection() {
        try {
            return connectionProvider.getConnection();
        } catch (SQLException e) {
            throw new EventStoreException("Failed to obtain a database connection", e);
        }
    }

    protected void executeUpdates(Consumer<SQLException> errorHandler, SqlFunction... sqlFunctions) {
        Connection connection = getConnection();
        try {
            Arrays.stream(sqlFunctions).forEach(sqlFunction -> {
                PreparedStatement preparedStatement = createSqlStatement(connection, sqlFunction);
                try {
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    errorHandler.accept(e);
                } finally {
                    closeQuietly(preparedStatement);
                }
            });
        } finally {
            closeQuietly(connection);
        }
    }

    protected void executeBatch(SqlFunction sqlFunction, Consumer<SQLException> errorHandler) {
        Connection connection = getConnection();
        try {
            PreparedStatement preparedStatement = createSqlStatement(connection, sqlFunction);
            try {
                preparedStatement.executeBatch();
            } catch (SQLException e) {
                errorHandler.accept(e);
            } finally {
                closeQuietly(preparedStatement);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    protected <R> List<R> executeQuery(SqlFunction sqlFunction, SqlResultsConverter<R> sqlResultsConverter,
                                       Function<SQLException, RuntimeException> errorHandler) {
        Connection connection = getConnection();
        try {
            PreparedStatement preparedStatement = createSqlStatement(connection, sqlFunction);
            try {
                ResultSet resultSet;
                try {
                    resultSet = preparedStatement.executeQuery();
                } catch (SQLException e) {
                    throw errorHandler.apply(e);
                }
                try {
                    return sqlResultsConverter.apply(resultSet);
                } catch (SQLException e) {
                    throw errorHandler.apply(e);
                } finally {
                    closeQuietly(resultSet);
                }
            } finally {
                closeQuietly(preparedStatement);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    protected static PreparedStatement createSqlStatement(Connection connection, SqlFunction sqlFunction) {
        try {
            return sqlFunction.apply(connection);
        } catch (SQLException e) {
            throw new EventStoreException("Failed to create a SQL statement", e);
        }
    }

    protected static <R> List<R> listResults(ResultSet resultSet,
                                           SqlResultConverter<R> singleResultConverter) throws SQLException {
        List<R> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(singleResultConverter.apply(resultSet));
        }
        return results;
    }

    @FunctionalInterface
    protected interface SqlFunction {
        PreparedStatement apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    protected interface SqlResultConverter<R> {
        R apply(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    protected interface SqlResultsConverter<R> {
        List<R> apply(ResultSet resultSet) throws SQLException;
    }
}
