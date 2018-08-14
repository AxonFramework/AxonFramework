/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.jdbc;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

import static junit.framework.TestCase.assertEquals;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.junit.Assert.assertFalse;

/**
 * Tests the JdbcEventStorageEngine using the MySQL database.
 *
 * @author Albert Attard (JavaCreed)
 */
public class MysqlJdbcEventStorageEngineTest {

    private MysqlDataSource dataSource;
    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;
    private JdbcEventStorageEngine testSubject;

    @Before
    public void setUp() throws Exception {
        /* Load the DB properties */
        final Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream("/mysql.test.database.properties"));

        dataSource = new MysqlDataSource();
        dataSource.setUrl(properties.getProperty("jdbc.url"));
        dataSource.setUser(properties.getProperty("jdbc.username"));
        dataSource.setPassword(properties.getProperty("jdbc.password"));
        try {
            defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
            testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver, new EventSchema(),
                                       byte[].class, MySqlEventTableFactory.INSTANCE);
        } catch (Exception e) {
            Assume.assumeNoException("Ignoring this test, as no valid MySQL instance is configured", e);
        }
    }

    /**
     * Issue #636 - The JdbcEventStorageEngine when used with the MySQL database
     * returns 0 instead of an empty optional when retrieving the last sequence
     * number for an aggregate that does not exist. This test replicates this
     * problem.
     */
    @Test
    public void testLoadLastSequenceNumber() {
        final String aggregateId = UUID.randomUUID().toString();
        testSubject.appendEvents(createEvent(aggregateId, 0), createEvent(aggregateId, 1));
        assertEquals(1L, (long) testSubject.lastSequenceNumberFor(aggregateId).orElse(-1L));
        assertFalse(testSubject.lastSequenceNumberFor("inexistent").isPresent());
    }

    protected JdbcEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                  PersistenceExceptionResolver persistenceExceptionResolver, EventSchema eventSchema, Class<?> dataType,
                                                  EventTableFactory tableFactory) {
        return createEngine(upcasterChain, persistenceExceptionResolver, null, eventSchema, dataType, tableFactory,
                            100);
    }

    protected JdbcEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                  PersistenceExceptionResolver persistenceExceptionResolver,
                                                  Predicate<? super DomainEventData<?>> snapshotFilter, EventSchema eventSchema, Class<?> dataType,
                                                  EventTableFactory tableFactory, int batchSize) {
        XStreamSerializer serializer = new XStreamSerializer();
        JdbcEventStorageEngine result = new JdbcEventStorageEngine(serializer, upcasterChain,
                                                                   persistenceExceptionResolver, serializer, snapshotFilter, batchSize, dataSource::getConnection,
                                                                   NoTransactionManager.INSTANCE, dataType, eventSchema, null, null);

        try {
            Connection connection = dataSource.getConnection();
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            result.createSchema(tableFactory);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
