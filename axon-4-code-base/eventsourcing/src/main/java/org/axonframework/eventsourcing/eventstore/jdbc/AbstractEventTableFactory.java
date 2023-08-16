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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Abstract implementation of an {@link EventTableFactory} that provides Jdbc "create table" statements compatible with
 * most databases.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class AbstractEventTableFactory implements EventTableFactory {

    @Override
    public PreparedStatement createDomainEventTable(Connection connection,
                                                    EventSchema schema) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + schema.domainEventTable() + " (\n" +
                schema.globalIndexColumn() + " " + idColumnType() + " NOT NULL,\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceNumberColumn() + " BIGINT NOT NULL,\n" +
                schema.typeColumn() + " VARCHAR(255),\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.metaDataColumn() + " " + payloadType() + ",\n" +
                schema.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timestampColumn() + " " + timestampType() + " ,\n" +
                "PRIMARY KEY (" + schema.globalIndexColumn() + "),\n" +
                "UNIQUE (" + schema.aggregateIdentifierColumn() + ", " +
                schema.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + schema.eventIdentifierColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    @Override
    public PreparedStatement createSnapshotEventTable(Connection connection,
                                                      EventSchema schema) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + schema.snapshotTable() + " (\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceNumberColumn() + " BIGINT NOT NULL,\n" +
                schema.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.metaDataColumn() + " " + payloadType() + ",\n" +
                schema.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timestampColumn() + " " + timestampType() + " ,\n" +
                "PRIMARY KEY (" + schema.aggregateIdentifierColumn() + ", " +
                schema.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + schema.eventIdentifierColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    /**
     * Returns the sql to register the auto incrementing global sequence column.
     *
     * @return the sql for the global id column
     */
    protected abstract String idColumnType();

    /**
     * Returns the sql to describe the type of payload column.
     *
     * @return the sql for the payload column
     */
    protected abstract String payloadType();

    /**
     * Returns the sql to describe the type of timestamp column.
     *
     * @return the sql for the timestamp column
     */
    protected String timestampType() {
        return " VARCHAR(255) NOT NULL ";
    }
}
