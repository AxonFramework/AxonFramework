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

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.Connection;
import java.sql.SQLException;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;

/**
 * @author Rene de Waele
 */
public class JdbcEventStorageEngineTest extends BatchingEventStorageEngineTest {

    private JDBCDataSource dataSource;
    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;
    private JdbcEventStorageEngine testSubject;

    @Before
    public void setUp() throws SQLException {
        dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");
        defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
        setTestSubject(testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver,
                                                  new EventSchema(), byte[].class, HsqlEventTableFactory.INSTANCE));
    }

    @Test
    public void testStoreTwoExactSameSnapshots() {
        testSubject.storeSnapshot(createEvent(1));
        testSubject.storeSnapshot(createEvent(1));
    }

    @Test
    @SuppressWarnings({"JpaQlInspection", "OptionalGetWithoutIsPresent"})
    @DirtiesContext
    public void testCustomSchemaConfig() throws Exception {
        setTestSubject(testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver,
                                                  EventSchema.builder().withEventTable("CustomDomainEvent")
                                                          .withPayloadColumn("eventData").build(), String.class,
                                                  new HsqlEventTableFactory() {
                                                      @Override
                                                      protected String payloadType() {
                                                          return "LONGVARCHAR";
                                                      }
                                                  }));
        testStoreAndLoadEvents();
    }

    @Override
    protected AbstractEventStorageEngine createEngine(EventUpcaster upcasterChain) {
        return createEngine(upcasterChain, defaultPersistenceExceptionResolver, new EventSchema(), byte[].class,
                            HsqlEventTableFactory.INSTANCE);

    }

    @Override
    protected AbstractEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        return createEngine(NoOpEventUpcaster.INSTANCE, persistenceExceptionResolver, new EventSchema(),
                            byte[].class, HsqlEventTableFactory.INSTANCE);
    }

    protected JdbcEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                          PersistenceExceptionResolver persistenceExceptionResolver,
                                                          EventSchema eventSchema, Class<?> dataType,
                                                          EventTableFactory tableFactory) {
        JdbcEventStorageEngine result =
                new JdbcEventStorageEngine(new XStreamSerializer(), upcasterChain, persistenceExceptionResolver, 100,
                                           dataSource::getConnection, NoTransactionManager.INSTANCE, dataType, eventSchema, null, null);
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
