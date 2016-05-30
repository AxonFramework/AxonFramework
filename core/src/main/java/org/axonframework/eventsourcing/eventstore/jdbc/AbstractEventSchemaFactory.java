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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventSchemaFactory implements EventSchemaFactory {
    @Override
    public PreparedStatement createDomainEventTable(Connection connection,
                                                    EventSchemaConfiguration configuration) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + configuration.domainEventTable() + " (\n" +
                configuration.globalIndexColumn() + " BIGINT " + autoIncrement() + " NOT NULL,\n" +
                configuration.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.sequenceNumberColumn() + " BIGINT NOT NULL,\n" +
                configuration.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.metaDataColumn() + " " + payloadType() + ",\n" +
                configuration.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                configuration.payloadRevisionColumn() + " VARCHAR(255),\n" +
                configuration.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + configuration.globalIndexColumn() + "),\n" +
                "UNIQUE (" + configuration.aggregateIdentifierColumn() + ", " +
                configuration.sequenceNumberColumn()+ "),\n" +
                "UNIQUE (" + configuration.eventIdentifierColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    @Override
    public PreparedStatement createSnapshotEventTable(Connection connection,
                                                      EventSchemaConfiguration configuration) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + configuration.snapshotTable() + " (\n" +
                configuration.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.sequenceNumberColumn() + " BIGINT NOT NULL,\n" +
                configuration.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.metaDataColumn() + " " + payloadType() + ",\n" +
                configuration.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                configuration.payloadRevisionColumn() + " VARCHAR(255),\n" +
                configuration.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + configuration.aggregateIdentifierColumn() + ", " +
                configuration.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + configuration.eventIdentifierColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    protected abstract String autoIncrement();

    protected abstract String payloadType();
}
