/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.junit.jupiter.api.*;
import org.opentest4j.TestAbortedException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the JdbcEventStorageEngine using the MySQL database.
 *
 * @author Albert Attard (JavaCreed)
 */
class MysqlJdbcEventStorageEngineTest {

    private MysqlDataSource dataSource;
    private JdbcEventStorageEngine testSubject;

    @BeforeEach
    void setUp() throws Exception {
        /* Load the DB properties */
        final Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream("/mysql.test.database.properties"));

        dataSource = new MysqlDataSource();
        dataSource.setUrl(properties.getProperty("jdbc.url"));
        dataSource.setUser(properties.getProperty("jdbc.username"));
        dataSource.setPassword(properties.getProperty("jdbc.password"));
        try {
            testSubject = createEngine(new SQLErrorCodesResolver(dataSource));
        } catch (Exception e) {
            throw new TestAbortedException("Ignoring this test, as no valid MySQL instance is configured", e);
        }
    }

    /**
     * Issue #636 - The JdbcEventStorageEngine when used with the MySQL database
     * returns 0 instead of an empty optional when retrieving the last sequence
     * number for an aggregate that does not exist. This test replicates this
     * problem.
     */
    @Test
    void testLoadLastSequenceNumber() {
        final String aggregateId = UUID.randomUUID().toString();
        testSubject.appendEvents(createEvent(aggregateId, 0), createEvent(aggregateId, 1));
        assertEquals(1L, (long) testSubject.lastSequenceNumberFor(aggregateId).orElse(-1L));
        assertFalse(testSubject.lastSequenceNumberFor("inexistent").isPresent());
    }

    private JdbcEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        JdbcEventStorageEngine result = JdbcEventStorageEngine.builder()
                                                              .persistenceExceptionResolver(persistenceExceptionResolver)
                                                              .connectionProvider(dataSource::getConnection)
                                                              .transactionManager(NoTransactionManager.INSTANCE)
                                                              .build();

        //noinspection Duplicates
        try {
            Connection connection = dataSource.getConnection();
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            result.createSchema(MySqlEventTableFactory.INSTANCE);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
