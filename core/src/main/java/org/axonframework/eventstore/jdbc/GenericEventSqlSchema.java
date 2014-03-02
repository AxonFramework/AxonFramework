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

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.axonframework.common.io.JdbcUtils.*;

/**
 * @author Kristian Rosenvold
 */
public class GenericEventSqlSchema implements EventSqlSchema {

    public static final String unprefixedFields = "eventIdentifier, aggregateIdentifier, sequenceNumber, timeStamp, payloadType, payloadRevision, payload, metaData";
    public static final String stdFields = "e.eventIdentifier, e.aggregateIdentifier, e.sequenceNumber, e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData";

    @Override
    public PreparedStatement sql_loadLastSnapshot(Connection connection, Object identifier, String aggregateType) {
        final String s = "select " + stdFields + " from SnapshotEventEntry e " +
                "WHERE e.aggregateIdentifier = ? AND e.type = ? ORDER BY e.sequenceNumber DESC";
        return createPreparedStatement(s, connection, identifier.toString(), aggregateType);
    }

    @Override
    public PreparedStatement sql_doInsert(String tableName, Connection connection, Object... params) {
        final String sql = "insert into " + tableName + " (" + unprefixedFields + ",type) values (?,?,?,?,?,?,?,?,?)";
        return createPreparedStatement(sql, connection, params);
    }

    @Override
    public PreparedStatement sql_pruneSnapshots(Connection connection, String type, Object aggregateIdentifier, Long sequenceOfFirstSnapshotToPrune) {
        final String sql = "DELETE FROM SnapshotEventEntry e "
                + "WHERE e.type = ? "
                + "AND e.aggregateIdentifier = ? "
                + "AND e.sequenceNumber <= ?";
        return createPreparedStatement(sql, connection, type, aggregateIdentifier.toString(), sequenceOfFirstSnapshotToPrune);
    }

    @Override
    public PreparedStatement sql_findRedundantSnapshots(Connection connection, String type, Object aggregateIdentifier) {
        final String sql = "SELECT e.sequenceNumber FROM SnapshotEventEntry e "
                + "WHERE e.type = ? AND e.aggregateIdentifier = ? "
                + "ORDER BY e.sequenceNumber DESC";
        return createPreparedStatement(sql, connection, type, aggregateIdentifier.toString());
    }

    @Override
    public PreparedStatement sql_fetchFromSequenceNumber(Connection connection, Object identifier, String type, long firstSequenceNumber) {
        final String sql = "select " + stdFields + " from DomainEventEntry e "
                + "WHERE e.aggregateIdentifier = ? AND type = ? "
                + "AND e.sequenceNumber >= ? "
                + "ORDER BY e.sequenceNumber ASC";
        return createPreparedStatement(sql, connection, identifier.toString(), type, firstSequenceNumber);
    }

    @Override
    public PreparedStatement sql_getFetchAll(String whereClause, Connection nonAutoCommittConnection, Object[] objects) {
        final String sql = "select " + stdFields + " from DomainEventEntry e " + getWhereClause(whereClause) +
                " ORDER BY e.timeStamp ASC, e.sequenceNumber ASC, e.aggregateIdentifier ASC ";
        return createPreparedStatement(sql, nonAutoCommittConnection, objects);
    }

    private String getWhereClause(String whereClause) {
        return whereClause != null ? "WHERE " + whereClause : "";
    }

    @Override
    public PreparedStatement sql_delete_all_snapshotEvenEntries(Connection connection) {
        return createPreparedStatement("DELETE FROM SnapshotEventEntry", connection);
    }

    @Override
    public PreparedStatement sql_deleteAllDomainEventEntries(Connection connection) {
        final String s = "DELETE FROM DomainEventEntry";
        return createPreparedStatement(s, connection);
    }

    @Override
    public PreparedStatement sql_createSnapshotEventEntry(Connection connection) {
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
        return createPreparedStatement(sql, connection);
    }

    @Override
    public PreparedStatement sql_createDomainEventEntry(Connection connection) {
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
        return createPreparedStatement(sql, connection);
    }
}
