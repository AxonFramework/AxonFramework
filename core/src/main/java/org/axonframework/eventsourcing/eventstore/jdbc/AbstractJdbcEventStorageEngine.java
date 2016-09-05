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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.*;
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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;
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
     * @param persistenceExceptionResolver Detects concurrency exceptions from the backing database. If {@code null},
     *                                     a .
     * @param transactionManager           The transaction manager used to set the isolation level of the transaction
     *                                     when loading events.
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
                                             TransactionManager transactionManager, Integer batchSize,
                                             ConnectionProvider connectionProvider) {
        super(serializer, upcasterChain, getOrDefault(persistenceExceptionResolver, new JdbcSQLErrorCodesResolver()),
              transactionManager, batchSize);
        this.connectionProvider = connectionProvider;
    }

    /**
     * Creates a statement to append the given {@code event} to the event storage using given {@code connection} to the
     * database. Use the given {@code serializer} to serialize the payload and metadata of the event.
     *
     * @param connection The connection to the database
     * @param event      The event to append
     * @param serializer The serializer that should be used when serializing the event's payload and metadata
     * @return A {@link PreparedStatement} that appends the event when executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected abstract PreparedStatement appendEvent(Connection connection, DomainEventMessage<?> event,
                                                     Serializer serializer) throws SQLException;

    /**
     * Creates a statement to append the given {@code snapshot} to the event storage using given {@code connection} to
     * the database. Use the given {@code serializer} to serialize the payload and metadata of the event.
     *
     * @param connection The connection to the database
     * @param snapshot   The snapshot to append
     * @param serializer The serializer that should be used when serializing the event's payload and metadata
     * @return A {@link PreparedStatement} that appends the snapshot when executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected abstract PreparedStatement appendSnapshot(Connection connection, DomainEventMessage<?> snapshot,
                                                        Serializer serializer) throws SQLException;

    /**
     * Creates a statement to delete all snapshots of the aggregate with given {@code aggregateIdentifier}.
     *
     * @param connection          The connection to the database
     * @param aggregateIdentifier The identifier of the aggregate whose snapshots to delete
     * @return A {@link PreparedStatement} that deletes all the aggregate's snapshots when executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected abstract PreparedStatement deleteSnapshots(Connection connection,
                                                         String aggregateIdentifier) throws SQLException;

    /**
     * Creates a statement to read domain event entries for an aggregate with given identifier starting with the first
     * entry having a sequence number that is equal or larger than the given {@code firstSequenceNumber}.
     *
     * @param connection          The connection to the database
     * @param identifier          The identifier of the aggregate
     * @param firstSequenceNumber The expected sequence number of the first returned entry
     * @return A {@link PreparedStatement} that returns event entries for the given query when executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected abstract PreparedStatement readEventData(Connection connection, String identifier,
                                                       long firstSequenceNumber) throws SQLException;

    /**
     * Creates a statement to read tracked event entries stored since given tracking token. Pass a {@code trackingToken}
     * of {@code null} to create a statement for all entries in the storage.
     *
     * @param connection The connection to the database
     * @param lastToken  Object describing the global index of the last processed event or {@code null} to return all
     *                   entries in the store
     * @return A {@link PreparedStatement} that returns event entries for the given query when executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected abstract PreparedStatement readEventData(Connection connection,
                                                       TrackingToken lastToken) throws SQLException;

    /**
     * Creates a statement to read the snapshot entry of an aggregate with given identifier
     *
     * @param connection The connection to the database
     * @param identifier The aggregate identifier
     * @return A {@link PreparedStatement} that returns the last snapshot entry of the aggregate (if any) when executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected abstract PreparedStatement readSnapshotData(Connection connection, String identifier) throws SQLException;

    /**
     * Extracts the next tracked event entry from the given {@code resultSet}.
     *
     * @param resultSet The results of a query for tracked events
     * @return The next tracked event
     * @throws SQLException when an exception occurs while creating the event data
     */
    protected abstract TrackedEventData<?> getTrackedEventData(ResultSet resultSet) throws SQLException;

    /**
     * Extracts the next domain event entry from the given {@code resultSet}.
     *
     * @param resultSet The results of a query for domain events of an aggregate
     * @return The next domain event
     * @throws SQLException when an exception occurs while creating the event data
     */
    protected abstract DomainEventData<?> getDomainEventData(ResultSet resultSet) throws SQLException;

    /**
     * Extracts the next snapshot entry from the given {@code resultSet}.
     *
     * @param resultSet The results of a query for a snapshot of an aggregate
     * @return The next snapshot data
     * @throws SQLException when an exception occurs while creating the event data
     */
    protected abstract DomainEventData<?> getSnapshotData(ResultSet resultSet) throws SQLException;

    /**
     * Performs the DDL queries to create the schema necessary for this storage engine implementation.
     *
     * @param schemaFactory factory of the event schema
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
                               .map(event -> connection -> appendEvent(connection, event, serializer)),
                       e -> handlePersistenceException(e, events.get(0)));
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

    private <R> List<R> executeQuery(SqlFunction sqlFunction, SqlResultConverter<R> sqlResultConverter,
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

    private PreparedStatement createSqlStatement(Connection connection, SqlFunction sqlFunction) {
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
