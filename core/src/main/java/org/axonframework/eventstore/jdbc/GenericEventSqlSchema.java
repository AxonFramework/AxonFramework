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

import org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventData;
import org.joda.time.DateTime;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @since 2.2
 */
@SuppressWarnings("JpaQueryApiInspection")
public class GenericEventSqlSchema implements EventSqlSchema {

    private static final String STD_FIELDS = "eventIdentifier, aggregateIdentifier, sequenceNumber, timeStamp, "
            + "payloadType, payloadRevision, payload, metaData";

    @Override
    public PreparedStatement sql_loadLastSnapshot(Connection connection, Object identifier, String aggregateType)
            throws SQLException {
        final String s = "SELECT " + STD_FIELDS + " FROM SnapshotEventEntry " +
                "WHERE aggregateIdentifier = ? AND type = ? ORDER BY sequenceNumber DESC";
        PreparedStatement statement = connection.prepareStatement(s);
        statement.setString(1, identifier.toString());
        statement.setString(2, aggregateType);
        return statement;
    }

    @Override
    public PreparedStatement sql_insertDomainEventEntry(Connection conn, String eventIdentifier,
                                                        String aggregateIdentifier, long sequenceNumber,
                                                        DateTime timestamp, String eventType, String eventRevision,
                                                        byte[] eventPayload, byte[] eventMetaData, String aggregateType)
            throws SQLException {
        return doInsertEventEntry("DomainEventEntry",
                                  conn, eventIdentifier, aggregateIdentifier, sequenceNumber, timestamp,
                                  eventType, eventRevision, eventPayload,
                                  eventMetaData, aggregateType);
    }

    @Override
    public PreparedStatement sql_insertSnapshotEventEntry(Connection conn, String eventIdentifier,
                                                          String aggregateIdentifier, long sequenceNumber,
                                                          DateTime timestamp, String eventType, String eventRevision,
                                                          byte[] eventPayload, byte[] eventMetaData,
                                                          String aggregateType) throws SQLException {
        return doInsertEventEntry("SnapshotEventEntry",
                                  conn, eventIdentifier, aggregateIdentifier, sequenceNumber, timestamp,
                                  eventType, eventRevision, eventPayload,
                                  eventMetaData, aggregateType);
    }

    /**
     * Creates a statement to insert an entry with given attributes in the given <code>tableName</code>. This method
     * is used by {@link #sql_insertDomainEventEntry(java.sql.Connection, String, String, long, org.joda.time.DateTime,
     * String, String, byte[], byte[], String)} and {@link #sql_insertSnapshotEventEntry(java.sql.Connection, String,
     * String, long, org.joda.time.DateTime, String, String, byte[], byte[], String)}, and provides an easier way to
     * change to types of columns used.
     *
     * @param tableName           The name of the table to insert the entry into
     * @param connection The connection to create the statement for
     * @param eventIdentifier     The unique identifier of the event
     * @param aggregateIdentifier The identifier of the aggregate that generated the event
     * @param sequenceNumber      The sequence number of the event
     * @param timestamp           The time at which the Event Message was generated
     * @param eventType           The type identifier of the serialized event
     * @param eventRevision       The revision of the serialized event
     * @param eventPayload        The serialized payload of the Event
     * @param eventMetaData       The serialized meta data of the event
     * @param aggregateType       The type identifier of the aggregate the event belongs to
     * @return
     *
     * @throws SQLException
     */
    protected PreparedStatement doInsertEventEntry(String tableName, Connection connection, String eventIdentifier,
                                                   String aggregateIdentifier,
                                                   long sequenceNumber, DateTime timestamp, String eventType,
                                                   String eventRevision,
                                                   byte[] eventPayload, byte[] eventMetaData, String aggregateType)
            throws SQLException {
        final String sql = "INSERT INTO " + tableName
                + " (eventIdentifier, type, aggregateIdentifier, sequenceNumber, timeStamp, payloadType, "
                + "payloadRevision, payload, metaData) VALUES (?,?,?,?,?,?,?,?,?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, eventIdentifier);
        preparedStatement.setString(2, aggregateType);
        preparedStatement.setString(3, aggregateIdentifier);
        preparedStatement.setLong(4, sequenceNumber);
        preparedStatement.setString(5, timestamp.toString());
        preparedStatement.setString(6, eventType);
        preparedStatement.setString(7, eventRevision);
        preparedStatement.setBytes(8, eventPayload);
        preparedStatement.setBytes(9, eventMetaData);
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_pruneSnapshots(Connection connection, String type, Object aggregateIdentifier,
                                                long sequenceOfFirstSnapshotToPrune) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM SnapshotEventEntry "
                                                                                  + "WHERE type = ? "
                                                                                  + "AND aggregateIdentifier = ? "
                                                                                  + "AND sequenceNumber <= ?");
        preparedStatement.setString(1, type);
        preparedStatement.setString(2, aggregateIdentifier.toString());
        preparedStatement.setLong(3, sequenceOfFirstSnapshotToPrune);
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_findSnapshotSequenceNumbers(Connection connection, String type,
                                                             Object aggregateIdentifier) throws SQLException {
        final String sql = "SELECT sequenceNumber FROM SnapshotEventEntry "
                + "WHERE type = ? AND aggregateIdentifier = ? "
                + "ORDER BY sequenceNumber DESC";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, type);
        preparedStatement.setString(2, aggregateIdentifier.toString());
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_fetchFromSequenceNumber(Connection connection, String type, Object aggregateIdentifier,
                                                         long firstSequenceNumber) throws SQLException {
        final String sql = "SELECT " + STD_FIELDS + " FROM DomainEventEntry "
                + "WHERE aggregateIdentifier = ? AND type = ? "
                + "AND sequenceNumber >= ? "
                + "ORDER BY sequenceNumber ASC";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, aggregateIdentifier.toString());
        preparedStatement.setString(2, type);
        preparedStatement.setLong(3, firstSequenceNumber);
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_getFetchAll(Connection connection, String whereClause,
                                             Object[] params) throws SQLException {
        final String sql = "select " + STD_FIELDS + " from DomainEventEntry e " + whereClause +
                " ORDER BY e.timeStamp ASC, e.sequenceNumber ASC, e.aggregateIdentifier ASC ";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof DateTime) {
                param = convertTimestampForWriting((DateTime) param);
            }

            if (param instanceof byte[]) {
                preparedStatement.setBytes(i + 1, (byte[]) param);
            } else {
                preparedStatement.setObject(i + 1, param);
            }
        }
        return preparedStatement;
    }

    protected Object convertTimestampForWriting(DateTime param) {
        return param.toString();
    }

    protected Object readTimeStamp(ResultSet resultSet, int rowIndex) throws SQLException {
        return resultSet.getString(rowIndex);
    }

    @Override
    public PreparedStatement sql_createSnapshotEventEntryTable(Connection connection) throws SQLException {
        final String sql = "    create table SnapshotEventEntry (\n" +
                "        aggregateIdentifier varchar(255) not null,\n" +
                "        sequenceNumber bigint not null,\n" +
                "        type varchar(255) not null,\n" +
                "        eventIdentifier varchar(255) not null,\n" +
                "        metaData blob,\n" +
                "        payload blob not null,\n" +
                "        payloadRevision varchar(255),\n" +
                "        payloadType varchar(255) not null,\n" +
                "        timeStamp varchar(255) not null,\n" +
                "        primary key (aggregateIdentifier, sequenceNumber, type)\n" +
                "    );";
        return connection.prepareStatement(sql);
    }

    @Override
    public PreparedStatement sql_createDomainEventEntryTable(Connection connection) throws SQLException {
        final String sql = "create table DomainEventEntry (\n" +
                "        aggregateIdentifier varchar(255) not null,\n" +
                "        sequenceNumber bigint not null,\n" +
                "        type varchar(255) not null,\n" +
                "        eventIdentifier varchar(255) not null,\n" +
                "        metaData blob,\n" +
                "        payload blob not null,\n" +
                "        payloadRevision varchar(255),\n" +
                "        payloadType varchar(255) not null,\n" +
                "        timeStamp varchar(255) not null,\n" +
                "        primary key (aggregateIdentifier, sequenceNumber, type)\n" +
                "    );\n";
        return connection.prepareStatement(sql);
    }

    @Override
    public SerializedDomainEventData createSerializedDomainEventData(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedDomainEventData(resultSet.getString(1), resultSet.getString(2),
                                                   resultSet.getLong(3), readTimeStamp(resultSet, 4),
                                                   resultSet.getString(5), resultSet.getString(6),
                                                   resultSet.getBytes(7), resultSet.getBytes(8));
    }
}
