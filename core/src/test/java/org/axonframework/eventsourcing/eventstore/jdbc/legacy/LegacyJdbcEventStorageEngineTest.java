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

package org.axonframework.eventsourcing.eventstore.jdbc.legacy;

import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchemaConfiguration;
import org.axonframework.eventsourcing.eventstore.jdbc.HsqlEventSchemaFactory;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.Before;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Rene de Waele
 */
public class LegacyJdbcEventStorageEngineTest extends BatchingEventStorageEngineTest {

    private JdbcEventStorageEngine testSubject;

    @Before
    public void setUp() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");

        testSubject = new JdbcEventStorageEngine(dataSource::getConnection, new LegacyEventSchema());
        testSubject.setPersistenceExceptionResolver(new SQLErrorCodesResolver(dataSource));
        setTestSubject(testSubject);

        Connection connection = dataSource.getConnection();
        connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
        connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
        testSubject.createSchema(new LegacyEventSchemaFactory());
    }

    private static class LegacyEventSchemaFactory extends HsqlEventSchemaFactory {
        @Override
        public PreparedStatement createDomainEventTable(Connection connection,
                                                        EventSchemaConfiguration configuration) throws SQLException {
            String sql = "CREATE TABLE IF NOT EXISTS " + configuration.domainEventTable() + " (\n" +
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
                    configuration.sequenceNumberColumn() + ", " +
                    configuration.typeColumn() + "),\n" +
                    "UNIQUE (" + configuration.eventIdentifierColumn() + ")\n" +
                    ")";
            return connection.prepareStatement(sql);
        }
    }


}
