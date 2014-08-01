/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.io.IOUtils;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.DataSourceConnectionProvider;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.sql.DataSource;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;

/**
 * Implementation of the EventEntryStore that stores events in DomainEventEntry table and snapshot events in
 * SnapshotEventEntry table.
 * <p/>
 *
 * @param <T> The type used when storing serialized data
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @author Knut-Olav Hoven
 * @since 2.2
 */
public class DefaultEventEntryStore<T> implements EventEntryStore<T> {

    private final ConnectionProvider connectionProvider;

    private final EventSqlSchema<T> sqlSchema;

    /**
     * Initialize the EventEntryStore, fetching connections from the given <code>dataSource</code> and executing SQL
     * statements using given <code>sqlSchema</code>.
     *
     * @param dataSource The data source used to create connections
     * @param sqlSchema  The SQL Definitions
     */
    public DefaultEventEntryStore(DataSource dataSource, EventSqlSchema<T> sqlSchema) {
        this(new UnitOfWorkAwareConnectionProviderWrapper(new DataSourceConnectionProvider(dataSource)), sqlSchema);
    }

    /**
     * Initialize the EventEntryStore, fetching connections from the given <code>connectionProvider</code> and
     * executing
     * SQL
     * statements using given <code>sqlSchema</code>.
     *
     * @param connectionProvider Used to obtain connections
     * @param sqlSchema          The SQL Definitions
     */
    public DefaultEventEntryStore(ConnectionProvider connectionProvider, EventSqlSchema<T> sqlSchema) {
        this.connectionProvider = connectionProvider;
        this.sqlSchema = sqlSchema;
    }

    /**
     * Initialize the EventEntryStore using a Generic SQL Schema, and given <code>connectionProvider</code> to obtain
     * connections.
     *
     * @param connectionProvider Used to obtain connections
     */
    @SuppressWarnings("unchecked")
    public DefaultEventEntryStore(ConnectionProvider connectionProvider) {
        this(connectionProvider, new GenericEventSqlSchema());
    }

    @Override
    public SerializedDomainEventData<T> loadLastSnapshotEvent(String aggregateType, Object identifier) {
        ResultSet result = null;
        Connection connection = null;
        try {
            connection = connectionProvider.getConnection();
            result = sqlSchema.sql_loadLastSnapshot(connection, identifier, aggregateType)
                              .executeQuery();
            if (result.next()) {
                return sqlSchema.createSerializedDomainEventData(result);
            }
            return null;
        } catch (SQLException e) {
            throw new EventStoreException("Exception while attempting to load last snapshot event of "
                                                  + aggregateType + "/" + identifier, e);
        } finally {
            closeQuietly(result);
            closeQuietly(connection);
        }
    }

    @Override
    public Iterator<SerializedDomainEventData<T>> fetchFiltered(String whereClause, List<Object> parameters,
                                                                int batchSize) {
        try {
            Connection connection = connectionProvider.getConnection();
            return new ConnectionResourceManagingIterator<T>(
                    new FilteredBatchingIterator<T>(whereClause, parameters, batchSize, sqlSchema, connection),
                    connection);
        } catch (SQLException e) {
            throw new EventStoreException("Exception while attempting to read from the Event Store database", e);
        }
        // we don't want to close the connection here. The ConnectionResourceManagingIterator will close the connection
        // when it finishes iterating the results.
    }


    @Override
    public void persistSnapshot(String aggregateType, DomainEventMessage snapshotEvent,
                                SerializedObject<T> serializedPayload, SerializedObject<T> serializedMetaData) {
        PreparedStatement preparedStatement = null;
        Connection connection = null;
        try {
            connection = connectionProvider.getConnection();
            preparedStatement = sqlSchema.sql_insertSnapshotEventEntry(connection,
                                                                       snapshotEvent.getIdentifier(),
                                                                       snapshotEvent.getAggregateIdentifier()
                                                                                    .toString(),
                                                                       snapshotEvent.getSequenceNumber(),
                                                                       snapshotEvent.getTimestamp(),
                                                                       serializedPayload.getType().getName(),
                                                                       serializedPayload.getType().getRevision(),
                                                                       serializedPayload.getData(),
                                                                       serializedMetaData.getData(),
                                                                       aggregateType);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new EventStoreException("Exception while attempting to persist a snapshot", e);
        } finally {
            closeQuietly(preparedStatement);
            closeQuietly(connection);
        }
    }

    @Override
    public void persistEvent(String aggregateType, DomainEventMessage event, SerializedObject<T> serializedPayload,
                             SerializedObject<T> serializedMetaData) {

        PreparedStatement preparedStatement = null;
        Connection connection = null;
        try {
            connection = connectionProvider.getConnection();
            preparedStatement = sqlSchema.sql_insertDomainEventEntry(connection,
                                                                     event.getIdentifier(),
                                                                     event.getAggregateIdentifier().toString(),
                                                                     event.getSequenceNumber(),
                                                                     event.getTimestamp(),
                                                                     serializedPayload.getType().getName(),
                                                                     serializedPayload.getType().getRevision(),
                                                                     serializedPayload.getData(),
                                                                     serializedMetaData.getData(),
                                                                     aggregateType
            );
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new EventStoreException("Exception occurred while attempting to persist an event", e);
        } finally {
            closeQuietly(preparedStatement);
            closeQuietly(connection);
        }
    }


    @Override
    public void pruneSnapshots(String type, DomainEventMessage mostRecentSnapshotEvent, int maxSnapshotsArchived) {
        Iterator<Long> redundantSnapshots = findRedundantSnapshots(type, mostRecentSnapshotEvent,
                                                                   maxSnapshotsArchived);
        if (redundantSnapshots.hasNext()) {
            long sequenceOfFirstSnapshotToPrune = redundantSnapshots.next();
            Connection connection = null;
            try {
                connection = connectionProvider.getConnection();
                executeUpdate(sqlSchema.sql_pruneSnapshots(connection,
                                                           type,
                                                           mostRecentSnapshotEvent.getAggregateIdentifier(),
                                                           sequenceOfFirstSnapshotToPrune), "prune snapshots");
            } catch (SQLException e) {
                throw new EventStoreException("An exception occurred while attempting to prune snapshots", e);
            } finally {
                closeQuietly(connection);
            }
        }
    }

    @Override
    public Class<T> getDataType() {
        return sqlSchema.getDataType();
    }

    /**
     * Finds the first of redundant snapshots, returned as an iterator for convenience purposes.
     *
     * @param type                 the type of the aggregate for which to find redundant snapshots
     * @param snapshotEvent        the last appended snapshot event
     * @param maxSnapshotsArchived the number of snapshots that may remain archived
     * @return an iterator over the snapshots found
     */
    private Iterator<Long> findRedundantSnapshots(String type, DomainEventMessage snapshotEvent,
                                                  int maxSnapshotsArchived) {
        ResultSet resultSet = null;
        PreparedStatement statement = null;
        Connection connection = null;
        try {
            connection = connectionProvider.getConnection();
            statement = sqlSchema.sql_findSnapshotSequenceNumbers(connection,
                                                                  type,
                                                                  snapshotEvent.getAggregateIdentifier());
            resultSet = statement.executeQuery();
            //noinspection StatementWithEmptyBody
            while (maxSnapshotsArchived-- > 0 && resultSet.next()) { // NOSONAR
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

    @Override
    public Iterator<SerializedDomainEventData<T>> fetchAggregateStream(String aggregateType, Object identifier,
                                                                    long firstSequenceNumber, int fetchSize) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = connectionProvider.getConnection();
            statement = sqlSchema.sql_fetchFromSequenceNumber(connection, aggregateType, identifier,
                                                              firstSequenceNumber);
            statement.setFetchSize(fetchSize);
            return new ConnectionResourceManagingIterator<T>(
                    new PreparedStatementIterator<T>(statement, sqlSchema),
                    connection);
        } catch (SQLException e) {
            closeQuietly(connection);
            closeQuietly(statement);
            throw new EventStoreException("Exception while attempting to read from an Aggregate Stream", e);
        }
    }

    private static class ConnectionResourceManagingIterator<T>
            implements Iterator<SerializedDomainEventData<T>>, Closeable {

        private final Iterator<SerializedDomainEventData<T>> inner;
        private final Connection connection;

        public ConnectionResourceManagingIterator(Iterator<SerializedDomainEventData<T>> inner, Connection connection) {
            this.inner = inner;
            this.connection = connection;
        }

        @Override
        public boolean hasNext() {
            return inner.hasNext();
        }

        @Override
        public SerializedDomainEventData<T> next() {
            return inner.next();
        }

        @Override
        public void remove() {
            inner.remove();
        }

        @Override
        public void close() throws IOException {
            IOUtils.closeQuietlyIfCloseable(inner);
            closeQuietly(connection);
        }
    }

    /**
     * This class will NOT close the connection pass into its constructor.
     */
    private static class FilteredBatchingIterator<T> implements Iterator<SerializedDomainEventData<T>>, Closeable {

        private final Connection connection;
        private PreparedStatementIterator<T> currentBatch;
        private SerializedDomainEventData<T> next;
        private SerializedDomainEventData<T>  lastItem;
        private final String whereClause;
        private final List<Object> parameters;
        private final int batchSize;
        private final EventSqlSchema<T> sqlSchema;

        public FilteredBatchingIterator(
                String whereClause,
                List<Object> parameters,
                int batchSize,
                EventSqlSchema<T> sqlSchema,
                Connection connection) {
            this.whereClause = whereClause;
            this.parameters = parameters;
            this.batchSize = batchSize;
            this.connection = connection;
            this.sqlSchema = sqlSchema;

            this.currentBatch = fetchBatch();
            if (currentBatch.hasNext()) {
                next = currentBatch.next();
            }
        }

        private PreparedStatementIterator<T> fetchBatch() {
            LinkedList<Object> params = new LinkedList<Object>(parameters);
            String batchWhereClause = buildWhereClause(params);
            try {
                final PreparedStatement sql = sqlSchema.sql_getFetchAll(
                        connection,
                        batchWhereClause,
                        params.toArray());
                sql.setMaxRows(batchSize);
                return new PreparedStatementIterator<T>(sql, sqlSchema);
            } catch (SQLException e) {
                throw new EventStoreException("Exception occurred while attempting to execute prepared statement", e);
            }
        }

        private String buildWhereClause(List<Object> params) {
            if (lastItem == null && whereClause == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (lastItem != null) {
                sb.append("(")
                  .append("(e.timeStamp > ?)")
                  .append(" OR ")
                  .append("(e.timeStamp = ? AND e.sequenceNumber > ?)")
                  .append(" OR ")
                  .append("(e.timeStamp = ? AND e.sequenceNumber = ? AND e.aggregateIdentifier > ?)")
                  .append(")");
                Object dateTimeSql = sqlSchema.sql_dateTime(lastItem.getTimestamp());
                params.add(0, dateTimeSql);

                params.add(1, dateTimeSql);
                params.add(2, lastItem.getSequenceNumber());

                params.add(3, dateTimeSql);
                params.add(4, lastItem.getSequenceNumber());
                params.add(5, lastItem.getAggregateIdentifier());
            }
            if (whereClause != null && whereClause.length() > 0) {
                if (lastItem != null) {
                    sb.append(" AND (");
                }
                sb.append(whereClause);
                if (lastItem != null) {
                    sb.append(")");
                }
            }
            if (sb.length() > 0) {
                sb.insert(0, "WHERE ");
            }
            return sb.toString();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public SerializedDomainEventData<T> next() {
            SerializedDomainEventData<T> current = next;
            lastItem = next;
            if (next != null && !currentBatch.hasNext() && currentBatch.readCount() >= batchSize) {
                IOUtils.closeQuietly(currentBatch);
                currentBatch = fetchBatch();
            }
            next = currentBatch.hasNext() ? currentBatch.next() : null;
            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Iterator is read-only");
        }

        @Override
        public void close() throws IOException {
            IOUtils.closeQuietly(currentBatch);
        }
    }

    private static class PreparedStatementIterator<T> implements Iterator<SerializedDomainEventData<T>>, Closeable {

        private final PreparedStatement statement;
        private final ResultSetIterator<T> rsIterator;

        public PreparedStatementIterator(PreparedStatement statement, EventSqlSchema<T> sqlSchema) {
            this.statement = statement;
            try {
                ResultSet resultSet = statement.executeQuery();
                rsIterator = new ResultSetIterator<T>(resultSet, sqlSchema);
            } catch (SQLException e) {
                throw new EventStoreException("Exception occurred while attempting to execute query on statement", e);
            }
        }

        public int readCount() {
            return rsIterator.readCount();
        }

        @Override
        public boolean hasNext() {
            return rsIterator.hasNext();
        }

        @Override
        public SerializedDomainEventData<T> next() {
            return rsIterator.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Iterator is read-only");
        }

        @Override
        public void close() throws IOException {
            rsIterator.close();
            closeQuietly(statement);
        }
    }

    private static class ResultSetIterator<T> implements Iterator<SerializedDomainEventData<T>>, Closeable {

        private final ResultSet rs;
        private final EventSqlSchema<T> sqlSchema;
        private boolean hasCalledNext = false;
        private boolean hasNext;
        private int counter = 0;

        public ResultSetIterator(ResultSet resultSet, EventSqlSchema<T> sqlSchema) {
            this.rs = resultSet;
            this.sqlSchema = sqlSchema;
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
        public SerializedDomainEventData<T> next() {
            try {
                establishNext();
                if (hasNext) {
                    counter++;
                }
                return sqlSchema.createSerializedDomainEventData(rs);
            } catch (SQLException e) {
                throw new EventStoreException("Exception occurred while attempting to read next event from ResultSet",
                                              e);
            } finally {
                hasCalledNext = false;
            }
        }

        public int readCount() {
            return counter;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Iterator is read-only");
        }

        @Override
        public void close() throws IOException {
            closeQuietly(rs);
        }
    }

    private int executeUpdate(PreparedStatement preparedStatement, String description) {
        try {
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new EventStoreException("Exception occurred while attempting to " + description, e);
        } finally {
            closeQuietly(preparedStatement);
        }
    }

    /**
     * Performs the DDL queries to create the schema necessary for this EventEntryStore implementation.
     *
     * @throws SQLException when an error occurs executing SQL statements
     */
    public void createSchema() throws SQLException {
        Connection connection = null;
        try {
            connection = connectionProvider.getConnection();
            executeUpdate(sqlSchema.sql_createDomainEventEntryTable(connection), "create domain event entry table");
            executeUpdate(sqlSchema.sql_createSnapshotEventEntryTable(connection), "create snapshot entry table");
        } finally {
            closeQuietly(connection);
        }
    }
}
