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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.axonframework.common.io.JdbcUtils.*;

/**
 * Implementation of the EventEntryStore that stores events in DomainEventEntry table and snapshot events in
 * SnapshotEventEntry table.
 * <p/>
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @since 2.1
 */
public class JdbcEventEntryStore implements EventEntryStore {

    private final DataSource dataSource;

    private final EventSqlSchema sqldef;

    public JdbcEventEntryStore(DataSource dataSource, EventSqlSchema sqldef) {
        this.dataSource = dataSource;
        this.sqldef = sqldef;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public SimpleSerializedDomainEventData loadLastSnapshotEvent(String aggregateType, Object identifier) {
        // Maybe do select top(1)
        ResultSet rs = null;
        try {
            rs = executeQuery(sqldef.sql_loadLastSnapshot(conn(), identifier, aggregateType));
            if (rs.next()) {
                return createSimpleSerializedDomainEventData(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            closeAllQuietly(rs);
        }
    }

    private static SimpleSerializedDomainEventData createSimpleSerializedDomainEventData(ResultSet rs) throws SQLException {
        return new SimpleSerializedDomainEventData(rs.getString(1), rs.getString(2),
                rs.getLong(3), rs.getTimestamp(4), rs.getString(5), rs.getString(6),
                rs.getBytes(7), rs.getBytes(8));
    }


    @Override
    @SuppressWarnings({"unchecked"})
    public Iterator<SerializedDomainEventData> fetchFiltered(String whereClause, List<Object> parameters, int batchSize) {
        return new BatchingIterator(fetchAll(batchSize, whereClause, parameters));
    }


    @Override
    public void persistSnapshot(String aggregateType, DomainEventMessage snapshotEvent,
                                SerializedObject serializedPayload, SerializedObject serializedMetaData) {
        doInsert(aggregateType, snapshotEvent, serializedPayload, serializedMetaData, "SnapshotEventEntry");
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public void persistEvent(String aggregateType, DomainEventMessage event, SerializedObject serializedPayload,
                             SerializedObject serializedMetaData) {
        doInsert(aggregateType, event, serializedPayload, serializedMetaData, "DomainEventEntry");
    }

    @SuppressWarnings("unchecked")
    private void doInsert(String aggregateType, DomainEventMessage snapshotEvent, SerializedObject serializedPayload,
                          SerializedObject serializedMetaData, String snapshotEventEntry) {

        byte[] data = (byte[]) serializedMetaData.getData();
        final PreparedStatement preparedStatement = sqldef.sql_doInsert(snapshotEventEntry, conn(),
                snapshotEvent.getIdentifier(),
                snapshotEvent.getAggregateIdentifier().toString(),
                snapshotEvent.getSequenceNumber(),
                new Timestamp(snapshotEvent.getTimestamp().getMillis()),
                serializedPayload.getType().getName(),
                serializedPayload.getType().getRevision(),
                ((SerializedObject<byte[]>) serializedPayload).getData(),
                Arrays.copyOf(data, data.length),
                aggregateType
        );
        executeUpdate(preparedStatement);
    }


    @Override
    public void pruneSnapshots(String type, DomainEventMessage mostRecentSnapshotEvent, int maxSnapshotsArchived) {
        Iterator<Long> redundantSnapshots = findRedundantSnapshots(type, mostRecentSnapshotEvent,
                maxSnapshotsArchived);
        if (redundantSnapshots.hasNext()) {
            Long sequenceOfFirstSnapshotToPrune = redundantSnapshots.next();
            executeUpdate(sqldef.sql_pruneSnapshots(conn(), type, mostRecentSnapshotEvent.getAggregateIdentifier(), sequenceOfFirstSnapshotToPrune));
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
        try {
            ResultSet resultSet = executeQuery(sqldef.sql_findRedundantSnapshots(conn(), type, snapshotEvent.getAggregateIdentifier()));
            //noinspection StatementWithEmptyBody
            while (maxSnapshotsArchived-- > 0 && resultSet.next()) {
                // ignore
            }
            List<Long> result = new ArrayList<Long>();
            while (resultSet.next()) {
                result.add(resultSet.getLong(1));
            }
            closeAllQuietly(resultSet);
            return result.iterator();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Iterator<SerializedDomainEventData> fetchAggregateStream(String aggregateType, Object identifier,
                                                                    long firstSequenceNumber,
                                                                    int fetchSize) {
        final PreparedStatement preparedStatement = sqldef.sql_fetchFromSequenceNumber(getNonAutoCommittConnection(dataSource), identifier, aggregateType, firstSequenceNumber);
        return new BatchingIterator(executeBatchingQuery(preparedStatement, fetchSize));
    }

    @SuppressWarnings("unchecked")
    private ResultSet fetchAll(int batchSize, String whereClause, List<Object> parameters) {
        final PreparedStatement sql = sqldef.sql_getFetchAll(whereClause, getNonAutoCommittConnection(dataSource), parameters.toArray());
        return executeBatchingQuery(sql , batchSize);
    }


    private static class BatchingIterator implements Iterator<SerializedDomainEventData>, Closeable {
        private final ResultSet rs;
        boolean hasCalledNext = false;
        boolean hasNext;

        public BatchingIterator(ResultSet resultSet) {
            this.rs = resultSet;
        }

        @Override
        public boolean hasNext() {
            try {
                establishNext();
                return hasNext;
            } catch (SQLException e) {
                throw new RuntimeException(e);
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
                return createSimpleSerializedDomainEventData(rs);
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
            closeBatchingPreparedStatement(rs);
        }
    }


    public void createSchema() throws SQLException {
        execute(sqldef.sql_createDomainEventEntry(conn()));
        execute(sqldef.sql_createSnapshotEventEntry(conn()));
    }

    public void deleteAllEventData() throws SQLException {
        execute(sqldef.sql_deleteAllDomainEventEntries(conn()));
        execute(sqldef.sql_delete_all_snapshotEvenEntries(conn()));
    }

    private Connection conn(){
        return getConnection(dataSource);
    }
}
