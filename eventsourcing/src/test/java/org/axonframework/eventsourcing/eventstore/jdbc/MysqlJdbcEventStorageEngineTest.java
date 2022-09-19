/*
 * Copyright (c) 2010-2021. Axon Framework
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

import com.mysql.cj.jdbc.MysqlDataSource;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.eventsourcing.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the JdbcEventStorageEngine using the MySQL database.
 *
 * @author Albert Attard (JavaCreed)
 */
@Testcontainers
class MysqlJdbcEventStorageEngineTest {

    @Container
    private static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql")
            .withDatabaseName("axon")
            .withUsername("admin")
            .withPassword("some-password");

    private JdbcEventStorageEngine testSubject;

    @BeforeEach
    void setUp() throws SQLException {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL_CONTAINER.getJdbcUrl());
        dataSource.setUser(MYSQL_CONTAINER.getUsername());
        dataSource.setPassword(MYSQL_CONTAINER.getPassword());
        testSubject = createEngine(dataSource);
    }

    /**
     * Issue #636 (https://github.com/AxonFramework/AxonFramework/issues/636) - The JdbcEventStorageEngine when used
     * with the MySQL database returns 0 instead of an empty optional when retrieving the last sequence number for an
     * aggregate that does not exist. This test replicates this problem.
     */
    @Test
    void loadLastSequenceNumber() {
        final String aggregateId = UUID.randomUUID().toString();
        testSubject.appendEvents(createEvent(aggregateId, 0), createEvent(aggregateId, 1));
        assertEquals(1L, (long) testSubject.lastSequenceNumberFor(aggregateId).orElse(-1L));
        assertFalse(testSubject.lastSequenceNumberFor("nonexistent").isPresent());
    }

    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    private JdbcEventStorageEngine createEngine(MysqlDataSource dataSource) throws SQLException {
        JdbcEventStorageEngine engine =
                JdbcEventStorageEngine.builder()
                                      .snapshotSerializer(TestSerializer.xStreamSerializer())
                                      .persistenceExceptionResolver(new SQLErrorCodesResolver(dataSource))
                                      .eventSerializer(TestSerializer.xStreamSerializer())
                                      .connectionProvider(dataSource::getConnection)
                                      .transactionManager(NoTransactionManager.INSTANCE)
                                      .build();

        try {
            Connection connection = dataSource.getConnection();
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            engine.createSchema(MySqlEventTableFactory.INSTANCE);
            return engine;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
