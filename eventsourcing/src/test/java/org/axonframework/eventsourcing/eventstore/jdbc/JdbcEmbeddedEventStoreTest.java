/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.eventsourcing.eventstore.EmbeddedEventStoreTest;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * An {@link EmbeddedEventStoreTest} implementation using the {@link JdbcEventStorageEngine} during testing.
 *
 * @author Steven van Beelen
 */
public class JdbcEmbeddedEventStoreTest extends EmbeddedEventStoreTest {

    private JDBCDataSource dataSource;

    @Override
    public EventStorageEngine createStorageEngine() {
        Serializer testSerializer = TestSerializer.JACKSON.getSerializer();
        if (dataSource == null) {
            dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:test");
        }
        return createTables(JdbcEventStorageEngine.builder()
                                                  .eventSerializer(testSerializer)
                                                  .snapshotSerializer(testSerializer)
                                                  .connectionProvider(dataSource::getConnection)
                                                  .transactionManager(transactionManager)
                                                  .build());
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    private JdbcEventStorageEngine createTables(JdbcEventStorageEngine testEngine) {
        try (Connection connection = dataSource.getConnection()) {
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            testEngine.createSchema(HsqlEventTableFactory.INSTANCE);
            return testEngine;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
