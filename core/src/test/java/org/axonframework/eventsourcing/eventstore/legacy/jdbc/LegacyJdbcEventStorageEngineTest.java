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

package org.axonframework.eventsourcing.eventstore.legacy.jdbc;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.jdbc.AbstractJdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.HsqlEventTableFactory;
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

    private AbstractJdbcEventStorageEngine testSubject;

    @Before
    public void setUp() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");

        testSubject = new LegacyJdbcEventStorageEngine(dataSource::getConnection, NoTransactionManager.INSTANCE);
        testSubject.setPersistenceExceptionResolver(new SQLErrorCodesResolver(dataSource));
        setTestSubject(testSubject);

        Connection connection = dataSource.getConnection();
        connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
        connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
        testSubject.createSchema(new LegacyEventTableFactory());
    }

    private static class LegacyEventTableFactory extends HsqlEventTableFactory {
        @Override
        public PreparedStatement createDomainEventTable(Connection connection,
                                                        EventSchema schema) throws SQLException {
            String sql = "CREATE TABLE IF NOT EXISTS " + schema.domainEventTable() + " (\n" +
                    schema.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                    schema.sequenceNumberColumn() + " BIGINT NOT NULL,\n" +
                    schema.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                    schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                    schema.metaDataColumn() + " " + payloadType() + ",\n" +
                    schema.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                    schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                    schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                    schema.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                    "PRIMARY KEY (" + schema.aggregateIdentifierColumn() + ", " +
                    schema.sequenceNumberColumn() + ", " +
                    schema.typeColumn() + "),\n" +
                    "UNIQUE (" + schema.eventIdentifierColumn() + ")\n" +
                    ")";
            return connection.prepareStatement(sql);
        }
    }


}
