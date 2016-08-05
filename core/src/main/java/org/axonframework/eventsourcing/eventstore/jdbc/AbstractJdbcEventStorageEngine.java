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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.serialization.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.common.io.IOUtils.closeQuietly;

/**
 * @author Rene de Waele
 */
public abstract class AbstractJdbcEventStorageEngine extends BatchingEventStorageEngine {
    private final ConnectionProvider connectionProvider;

    public AbstractJdbcEventStorageEngine(ConnectionProvider connectionProvider,
                                          TransactionManager transactionManager) {
        super(transactionManager);
        this.connectionProvider = connectionProvider;
    }

    protected abstract PreparedStatement appendEvent(Connection connection, DomainEventMessage<?> event,
                                                     Serializer serializer) throws SQLException;

    protected abstract PreparedStatement appendSnapshot(Connection connection, DomainEventMessage<?> snapshot,
                                                        Serializer serializer) throws SQLException;

    protected abstract PreparedStatement deleteSnapshots(Connection connection,
                                                         String aggregateIdentifier) throws SQLException;

    protected abstract PreparedStatement readEventData(Connection connection, String identifier,
                                                       long firstSequenceNumber) throws SQLException;

    protected abstract PreparedStatement readEventData(Connection connection,
                                                       TrackingToken lastToken) throws SQLException;

    protected abstract PreparedStatement readSnapshotData(Connection connection, String identifier) throws SQLException;

    protected abstract TrackedEventData<?> getTrackedEventData(ResultSet resultSet) throws SQLException;

    protected abstract DomainEventData<?> getDomainEventData(ResultSet resultSet) throws SQLException;

    protected abstract DomainEventData<?> getSnapshotData(ResultSet resultSet) throws SQLException;

    /**
     * Performs the DDL queries to create the schema necessary for this storage engine implementation.
     *
     * @throws EventStoreException when an error occurs executing SQL statements
     */
    public abstract void createSchema(EventTableFactory schemaFactory);

    @Override
    protected List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
        return executeQuery(connection -> {
                                PreparedStatement statement = readEventData(connection, lastToken);
                                statement.setMaxRows(batchSize);
                                return statement;
                            }, this::getTrackedEventData,
                            e -> new EventStoreException(format("Failed to read events from token [%s]", lastToken),
                                                         e));
    }

    @Override
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return executeQuery(connection -> {
            PreparedStatement statement = readEventData(connection, aggregateIdentifier, firstSequenceNumber);
            statement.setMaxRows(batchSize);
            return statement;
        }, this::getDomainEventData, e -> new EventStoreException(
                format("Failed to read events for aggregate [%s]", aggregateIdentifier), e));
    }

    @Override
    protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
        if (events.isEmpty()) {
            return;
        }
        executeUpdates(events.stream().map(EventUtils::asDomainEventMessage)
                               .map(event -> connection -> appendEvent(connection, event, serializer)), e -> {
            handlePersistenceException(e, events.get(0));
        });
    }

    @Override
    protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
        executeUpdates(e -> handlePersistenceException(e, snapshot),
                       connection -> deleteSnapshots(connection, snapshot.getAggregateIdentifier()),
                       connection -> appendSnapshot(connection, snapshot, serializer));
    }

    @Override
    protected Optional<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        List<DomainEventData<?>> result =
                executeQuery(connection -> readSnapshotData(connection, aggregateIdentifier), this::getSnapshotData,
                             e -> new EventStoreException(
                                     format("Error reading aggregate snapshot [%s]", aggregateIdentifier), e));
        return result.stream().findFirst();
    }

    protected void executeUpdates(Consumer<SQLException> errorHandler, SqlFunction... sqlFunctions) {
        executeUpdates(Arrays.stream(sqlFunctions), errorHandler);
    }

    protected void executeUpdates(Stream<SqlFunction> sqlFunctions, Consumer<SQLException> errorHandler) {
        Connection connection = getConnection();
        try {
            sqlFunctions.forEach(sqlFunction -> {
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

    protected <R> List<R> executeQuery(SqlFunction sqlFunction, SqlResultConverter<R> sqlResultConverter,
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
                    List<R> results = new ArrayList<>();
                    while (resultSet.next()) {
                        results.add(sqlResultConverter.apply(resultSet));
                    }
                    return results;
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

    protected Connection getConnection() {
        try {
            return connectionProvider.getConnection();
        } catch (SQLException e) {
            throw new EventStoreException("Failed to obtain a database connection", e);
        }
    }

    protected PreparedStatement createSqlStatement(Connection connection, SqlFunction sqlFunction) {
        try {
            return sqlFunction.apply(connection);
        } catch (SQLException e) {
            throw new EventStoreException("Failed to create a SQL statement", e);
        }
    }

    @FunctionalInterface
    protected interface SqlFunction {
        PreparedStatement apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    protected interface SqlResultConverter<R> {
        R apply(ResultSet resultSet) throws SQLException;
    }
}
