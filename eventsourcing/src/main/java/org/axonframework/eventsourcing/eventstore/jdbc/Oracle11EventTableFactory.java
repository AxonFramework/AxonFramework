/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.common.jdbc.Oracle11Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Oracle 11 doesn't support the data type BIGINT, so NUMBER(19) is used as a substitute instead. Also Oracle doesn't
 * seem to like colons in create table statements, so those have been removed.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class Oracle11EventTableFactory extends AbstractEventTableFactory {

    @Override
    public PreparedStatement createDomainEventTable(Connection connection, EventSchema schema) throws SQLException {
        String sql = "CREATE TABLE " + schema.domainEventTable() + " (\n" +
                schema.globalIndexColumn() + " NUMBER(19) NOT NULL,\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceNumberColumn() + " NUMBER(19) NOT NULL,\n" +
                schema.typeColumn() + " VARCHAR(255),\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.metaDataColumn() + " " + payloadType() + ",\n" +
                schema.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + schema.globalIndexColumn() + "),\n" +
                "UNIQUE (" + schema.aggregateIdentifierColumn() + ", " +
                schema.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + schema.eventIdentifierColumn() + ")\n" +
                ")";
        try (PreparedStatement pst = connection.prepareStatement(sql)) {
            pst.execute();
        }
        Oracle11Utils.simulateAutoIncrement(connection, schema.domainEventTable(), schema.globalIndexColumn());

        return Oracle11Utils.createNullStatement(connection);
    }

    @Override
    public PreparedStatement createSnapshotEventTable(Connection connection, EventSchema schema) throws SQLException {
        String sql = "CREATE TABLE " + schema.snapshotTable() + " (\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceNumberColumn() + " NUMBER(19) NOT NULL,\n" +
                schema.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.metaDataColumn() + " " + payloadType() + ",\n" +
                schema.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + schema.aggregateIdentifierColumn() + ", " +
                schema.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + schema.eventIdentifierColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    @Override
    protected String idColumnType() {
        return ""; // ignored
    }

    @Override
    protected String payloadType() {
        return "BLOB";
    }
}
