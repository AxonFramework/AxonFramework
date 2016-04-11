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

package org.axonframework.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Rene de Waele
 */
public class MySqlEventSchemaFactory implements EventSchemaFactory {

    @Override
    public PreparedStatement createDomainEventTable(Connection connection,
                                                    EventSchemaConfiguration configuration) throws SQLException {
        String sql = " create table " + configuration.domainEventTable() + " (\n" +
                configuration.globalIndexColumn() + " bigint NOT NULL AUTO_INCREMENT,\n" +
                configuration.aggregateIdentifierColumn() + " varchar(255) not null,\n" +
                configuration.sequenceNumberColumn() + " bigint not null,\n" +
                configuration.typeColumn() + " varchar(255) not null,\n" +
                configuration.eventIdentifierColumn() + " varchar(255) not null,\n" +
                configuration.metaDataColumn() + " blob,\n" +
                configuration.payloadColumn() + " blob not null,\n" +
                configuration.payloadRevisionColumn() + " varchar(255),\n" +
                configuration.payloadTypeColumn() + " varchar(255) not null,\n" +
                configuration.timestampColumn() + " varchar(255) not null,\n" +
                "constraint primary key (" + configuration.globalIndexColumn() + "),\n" +
                "constraint unique (" + configuration.aggregateIdentifierColumn() + ", " +
                configuration.sequenceNumberColumn() + "),\n" +
                "constraint unique (eventIdentifier)\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    @Override
    public PreparedStatement createSnapshotEventTable(Connection connection,
                                                      EventSchemaConfiguration configuration) throws SQLException {
        String sql = " create table " + configuration.snapshotTable() + " (\n" +
                configuration.aggregateIdentifierColumn() + " varchar(255) not null,\n" +
                configuration.sequenceNumberColumn() + " bigint not null,\n" +
                configuration.typeColumn() + " varchar(255) not null,\n" +
                configuration.eventIdentifierColumn() + " varchar(255) not null,\n" +
                configuration.metaDataColumn() + " blob,\n" +
                configuration.payloadColumn() + " blob not null,\n" +
                configuration.payloadRevisionColumn() + " varchar(255),\n" +
                configuration.payloadTypeColumn() + " varchar(255) not null,\n" +
                configuration.timestampColumn() + " varchar(255) not null,\n" +
                "constraint primary key (" + configuration.aggregateIdentifierColumn() + ", " +
                configuration.sequenceNumberColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

}
