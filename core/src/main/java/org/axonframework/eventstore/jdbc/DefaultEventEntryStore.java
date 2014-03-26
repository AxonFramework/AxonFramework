/*
 * Copyright (c) 2010-2013. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.axonframework.eventstore.jdbc;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.DataSourceConnectionProvider;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.saga.SagaStorageException;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.sql.DataSource;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;

/**
 * Implementation of the EventEntryStore that stores events in DomainEventEntry table and snapshot events in
 * SnapshotEventEntry table.
 * <p/>
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @since 2.2
 */
public class DefaultEventEntryStore implements EventEntryStore {

    private final ConnectionProvider connectionProvider;

    private final EventSqlSchema sqldef;

    /**
     * Initialize the EventEntryStore, fetching connections from the given <code>dataSource</code> and executing SQL
     * statements using given <code>sqldef</code>.
     *
     * @param dataSource The data source used to create connections
     * @param sqlSchema  The SQL Definitions
     */
    public DefaultEventEntryStore(DataSource dataSource, EventSqlSchema sqlSchema) {
        this(new UnitOfWorkAwareConnectionProviderWrapper(new DataSourceConnectionProvider(dataSource)), sqlSchema);
    }

    /**
     * Initialize the EventEntryStore, fetching connections from the given <code>connectionProvider</code> and
     * executing
     * SQL
     * statements using given <code>sqldef</code>.
     *
     * @param connectionProvider Used to obtain connections
     * @param sqlSchema          The SQL Definitions
     */
    public DefaultEventEntryStore(ConnectionProvider connectionProvider, EventSqlSchema sqlSchema) {
        this.connectionProvider = connectionProvider;
        this.sqldef = sqlSchema;
    }

    /**
     * Initialize the EventEntryStore using a Generic SQL Schema, and given <code>connectionProvider</code> to obtain
     * connections.
     *
     * @param connectionProvider Used to obtain connections
     */
    public DefaultEventEntryStore(ConnectionProvider connectionProvider) {
        this(connectionProvider, new GenericEventSqlSchema());
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public SerializedDomainEventData loadLastSnapshotEvent(String aggregateType, Object identifier) {
        ResultSet result = null;
        try {
            result = sqldef.sql_loadLastSnapshot(connectionProvider.getConnection(), identifier, aggregateType)
                           .executeQuery();
            if (result.next()) {
                return sqldef.createSerializedDomainEventData(result);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(result);
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Iterator<SerializedDomainEventData> fetchFiltered(String whereClause, List<Object> parameters,
                                                             int batchSize) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = connectionProvider.getConnection();
            statement = sqldef.sql_getFetchAll(connection, whereClause, parameters.toArray());
            statement.setFetchSize(batchSize);
            return new ResultSetIterator(statement.executeQuery(), statement, connection);
        } catch (SQLException e) {
            closeQuietly(connection);
            closeQuietly(statement);
            throw new EventStoreException("Exception while attempting to read from the Event Store database", e);
        }
    }


    @Override
    public void persistSnapshot(String aggregateType, DomainEventMessage snapshotEvent,
                                SerializedObject serializedPayload, SerializedObject serializedMetaData) {
        byte[] data = (byte[]) serializedMetaData.getData();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = sqldef.sql_insertSnapshotEventEntry(connectionProvider.getConnection(),
                                                                    snapshotEvent.getIdentifier(),
                                                                    snapshotEvent.getAggregateIdentifier()
                                                                                 .toString(),
                                                                    snapshotEvent.getSequenceNumber(),
                                                                    snapshotEvent.getTimestamp(),
                                                                    serializedPayload.getType().getName(),
                                                                    serializedPayload.getType().getRevision(),
                                                                    (byte[]) serializedPayload.getData(),
                                                                    Arrays.copyOf(data, data.length),
                                                                    aggregateType
            );
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new EventStoreException("Exception while attempting to persist a snapshot", e);
        } finally {
            closeQuietly(preparedStatement);
        }
    }

    @Override
    public void persistEvent(String aggregateType, DomainEventMessage event, SerializedObject serializedPayload,
                             SerializedObject serializedMetaData) {

        PreparedStatement preparedStatement = null;
        try {
            byte[] data = (byte[]) serializedMetaData.getData();
            preparedStatement = sqldef.sql_insertDomainEventEntry(connectionProvider.getConnection(),
                                                                  event.getIdentifier(),
                                                                  event.getAggregateIdentifier().toString(),
                                                                  event.getSequenceNumber(),
                                                                  event.getTimestamp(),
                                                                  serializedPayload.getType().getName(),
                                                                  serializedPayload.getType().getRevision(),
                                                                  (byte[]) serializedPayload.getData(),
                                                                  Arrays.copyOf(data, data.length),
                                                                  aggregateType
            );
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to persist an event", e);
        } finally {
            closeQuietly(preparedStatement);
        }
    }


    @Override
    public void pruneSnapshots(String type, DomainEventMessage mostRecentSnapshotEvent, int maxSnapshotsArchived) {
        Iterator<Long> redundantSnapshots = findRedundantSnapshots(type, mostRecentSnapshotEvent,
                                                                   maxSnapshotsArchived);
        if (redundantSnapshots.hasNext()) {
            long sequenceOfFirstSnapshotToPrune = redundantSnapshots.next();
            try {
                executeUpdate(sqldef.sql_pruneSnapshots(connectionProvider.getConnection(),
                                                        type,
                                                        mostRecentSnapshotEvent.getAggregateIdentifier(),
                                                        sequenceOfFirstSnapshotToPrune), "prune snapshots");
            } catch (SQLException e) {
                throw new EventStoreException("An exception occurred while attempting to prune snapshots", e);
            }
        }
    }

    /**
     * Finds the first of redundant snapshots, returned as an iterator for convenience purposes.
     *
     * @param type                 the type of the aggregate for which to find redundant snapshots
     * @param snapshotEvent        the last appended snapshot event
     * @param maxSnapshotsArchived the number of snapshots that may remain archived
     * @return an iterator over the snapshots found
     */
    @SuppressWarnings({"unchecked"})
    private Iterator<Long> findRedundantSnapshots(String type, DomainEventMessage snapshotEvent,
                                                  int maxSnapshotsArchived) {
        ResultSet resultSet = null;
        PreparedStatement statement = null;
        Connection connection = null;
        try {
            connection = connectionProvider.getConnection();
            statement = sqldef.sql_findSnapshotSequenceNumbers(connection,
                                                               type,
                                                               snapshotEvent.getAggregateIdentifier());
            resultSet = statement.executeQuery();
            //noinspection StatementWithEmptyBody
            while (maxSnapshotsArchived-- > 0 && resultSet.next()) {
                // ignore
            }
            List<Long> result = new ArrayList<Long>();
            while (resultSet.next()) {
                result.add(resultSet.getLong(1));
            }
            resultSet.close();
            return result.iterator();
        } catch (SQLException e) {
            throw new EventStoreException("Exception ", e);
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Iterator<SerializedDomainEventData> fetchAggregateStream(String aggregateType, Object identifier,
                                                                    long firstSequenceNumber, int fetchSize) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = connectionProvider.getConnection();
            statement = sqldef.sql_fetchFromSequenceNumber(connection, aggregateType, identifier,
                                                           firstSequenceNumber);
            statement.setFetchSize(fetchSize);
            return new ResultSetIterator(statement.executeQuery(), statement, connection);
        } catch (SQLException e) {
            closeQuietly(connection);
            closeQuietly(statement);
            throw new EventStoreException("Exception while attempting to read from an Aggregate Stream", e);
        }
    }


    private class ResultSetIterator implements Iterator<SerializedDomainEventData>, Closeable {

        private final ResultSet rs;
        private final PreparedStatement statement;
        private final Connection connection;
        boolean hasCalledNext = false;
        boolean hasNext;

        public ResultSetIterator(ResultSet resultSet, PreparedStatement statement, Connection connection) {
            this.rs = resultSet;
            this.statement = statement;
            this.connection = connection;
        }

        @Override
        public boolean hasNext() {
            try {
                establishNext();
                return hasNext;
            } catch (SQLException e) {
                throw new EventStoreException("Exception occurred while attempting to fetch data from ResultSet", e);
            }
        }

        private void establishNext() throws SQLException {
            if (!hasCalledNext) {
                hasNext = rs.next();
                hasCalledNext = true;
            }
        }

        @Override
        public SerializedDomainEventData next() {
            try {
                establishNext();
                return sqldef.createSerializedDomainEventData(rs);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                hasCalledNext = false;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void close() throws IOException {
            closeQuietly(rs);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private int executeUpdate(PreparedStatement preparedStatement, String description) {
        try {
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to " + description, e);
        } finally {
            closeQuietly(preparedStatement);
        }
    }

    public void createSchema() throws SQLException {
        executeUpdate(sqldef.sql_createDomainEventEntryTable(connectionProvider.getConnection()),
                      "create domain event entry table");
        executeUpdate(sqldef.sql_createSnapshotEventEntryTable(connectionProvider.getConnection()),
                      "create snapshot entry table");
    }
}
