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

import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
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

    private JdbcEventStorageEngine testSubject;

    @Before
    public void setUp() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");
        testSubject = new JdbcEventStorageEngine(dataSource::getConnection);
        testSubject.setPersistenceExceptionResolver(new SQLErrorCodesResolver(dataSource));
        setTestSubject(testSubject);

        Connection connection = dataSource.getConnection();
        connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
        connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
        testSubject.createSchema(HsqlEventSchemaFactory.INSTANCE);
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
        EventSchemaConfiguration customConfiguration = EventSchemaConfiguration.builder()
                .withEventTable("CustomDomainEvent").withPayloadColumn("eventData").build();

        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");

        EventSchema eventSchema = new DefaultEventSchema(customConfiguration, String.class);

        testSubject = new JdbcEventStorageEngine(dataSource::getConnection, eventSchema);
        testSubject.setPersistenceExceptionResolver(new SQLErrorCodesResolver(dataSource));
        setTestSubject(testSubject);

        Connection connection = dataSource.getConnection();
        connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
        connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
        testSubject.createSchema(new HsqlEventSchemaFactory() {
            @Override
            protected String payloadType() {
                return "LONGVARCHAR";
            }
        });

        testStoreAndLoadEvents();
    }
}
