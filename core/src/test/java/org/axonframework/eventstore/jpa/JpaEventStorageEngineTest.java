/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.eventstore.jpa;

import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventstore.SerializedDomainEventData;
import org.axonframework.eventstore.SerializedEventData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.UnknownSerializedTypeException;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.UpcasterChain;
import org.axonframework.upcasting.UpcastingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.axonframework.eventstore.EventStoreTestUtils.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/db-context.xml")
public class JpaEventStorageEngineTest {

    private static final int BATCH_SIZE = 100;
    private JpaEventStorageEngine testSubject;

    @PersistenceContext
    private EntityManager entityManager;

    private EntityManagerProvider entityManagerProvider;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager txManager;

    private Serializer serializer = new XStreamSerializer();

    private TransactionTemplate template;

    @Before
    public void setUp() throws SQLException {
        entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
        testSubject = new JpaEventStorageEngine(entityManagerProvider);
        testSubject.setPersistenceExceptionResolver(new SQLErrorCodesResolver(dataSource));
        testSubject.setBatchSize(BATCH_SIZE);

        template = new TransactionTemplate(txManager);
        template.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                entityManager.createQuery("DELETE FROM DomainEventEntry").executeUpdate();
                entityManager.createQuery("DELETE FROM SnapshotEventEntry").executeUpdate();
            }
        });
    }

    @Test(expected = DataIntegrityViolationException.class)
    public void testUniqueKeyConstraintOnEventIdentifier() {
        template.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                entityManager.persist(new DomainEventEntry(createEvent("id", AGGREGATE, 0), serializer));
            }
        });
        template.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                entityManager.persist(new DomainEventEntry(createEvent("id", "otherAggregate", 0), serializer));
            }
        });
    }

    @Transactional
    @Test(expected = UnknownSerializedTypeException.class)
    public void testUnknownSerializedTypeCausesException() {
        testSubject.appendEvents(createEvents(1));
        entityManager.flush();
        entityManager.clear();
        entityManager.createQuery("UPDATE DomainEventEntry e SET e.payloadType = :type").setParameter("type", "unknown")
                .executeUpdate();
        testSubject.readEvents(AGGREGATE);
    }

    @Transactional
    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(testSubject);
        testSubject.appendEvents(createEvents(4));
        entityManager.flush();
        assertEquals(4L, entityManager.createQuery("SELECT count(e) FROM DomainEventEntry e").getSingleResult());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents(createEvent("other", 0).withMetaData(singletonMap("key", "Value")));
        entityManager.flush();
        entityManager.clear();

        List<DomainEventMessage> actualEvents = testSubject.readEvents(AGGREGATE).collect(Collectors.toList());
        assertEquals(4, actualEvents.size());

        /// we make sure persisted events have the same MetaData alteration logic
        List<DomainEventMessage<?>> other = testSubject.readEvents("other").collect(Collectors.toList());
        assertFalse(other.isEmpty());
        DomainEventMessage<?> messageWithMetaData = other.get(0);
        DomainEventMessage<?> altered = messageWithMetaData.withMetaData(singletonMap("key2", "value"));
        DomainEventMessage<?> combined = messageWithMetaData.andMetaData(singletonMap("key2", "value"));
        assertTrue(altered.getMetaData().containsKey("key2"));
        altered.getPayload();
        assertFalse(altered.getMetaData().containsKey("key"));
        assertTrue(altered.getMetaData().containsKey("key2"));
        assertTrue(combined.getMetaData().containsKey("key"));
        assertTrue(combined.getMetaData().containsKey("key2"));
        assertNotNull(messageWithMetaData.getPayload());
        assertNotNull(messageWithMetaData.getMetaData());
        assertFalse(messageWithMetaData.getMetaData().isEmpty());
    }

    @DirtiesContext
    @Test
    @Transactional
    public void testStoreAndLoadEvents_WithUpcaster() {
        UpcasterChain mockUpcasterChain = mock(UpcasterChain.class);
        when(mockUpcasterChain.upcast(isA(SerializedObject.class), isA(UpcastingContext.class)))
                .thenAnswer(invocation -> {
                    SerializedObject serializedObject = (SerializedObject) invocation.getArguments()[0];
                    return asList(serializedObject, serializedObject);
                });
        testSubject.setUpcasterChain(mockUpcasterChain);

        testSubject.appendEvents(createEvents(4));
        entityManager.flush();
        assertEquals(4L, entityManager.createQuery("SELECT count(e) FROM DomainEventEntry e").getSingleResult());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents(createEvent("other", 0).withMetaData(singletonMap("key", "Value")));
        entityManager.flush();
        entityManager.clear();

        List<DomainEventMessage> actualEvents = testSubject.readEvents(AGGREGATE).collect(Collectors.toList());

        assertEquals(8, actualEvents.size());
        for (int t = 0; t < 8; t = t + 2) {
            assertEquals(actualEvents.get(t).getSequenceNumber(), actualEvents.get(t + 1).getSequenceNumber());
            assertEquals(actualEvents.get(t).getAggregateIdentifier(),
                         actualEvents.get(t + 1).getAggregateIdentifier());
            assertEquals(actualEvents.get(t).getMetaData(), actualEvents.get(t + 1).getMetaData());
            assertNotNull(actualEvents.get(t).getPayload());
            assertNotNull(actualEvents.get(t + 1).getPayload());
        }
    }

    @Test
    @Transactional
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testLoad_LargeAmountOfEvents() {
        int eventCount = BATCH_SIZE + 10;
        testSubject.appendEvents(createEvents(eventCount));
        entityManager.flush();
        entityManager.clear();
        assertEquals(eventCount, testSubject.readEvents(AGGREGATE).reduce((a, b) -> b).get().getSequenceNumber());
        assertEquals(eventCount, testSubject.readEvents(AGGREGATE).count());
    }

    @DirtiesContext
    @Test
    @Transactional
    public void testLoad_LargeAmountOfEventsInSmallBatches() {
        testSubject.setBatchSize(10);
        testLoad_LargeAmountOfEvents();
    }

    @Test
    @Transactional
    public void testLoadNonExistent() {
        assertEquals(0L, testSubject.readEvents(UUID.randomUUID().toString()).count());
    }

    @Transactional
    @Test(expected = ConcurrencyException.class)
    public void testInsertDuplicateSnapshot() throws Exception {
        testSubject.storeSnapshot(createEvent(1));
        testSubject.storeSnapshot(createEvent(1));
    }

    @Test(expected = ConcurrencyException.class)
    @Transactional
    public void testStoreDuplicateEvent_WithSqlExceptionTranslator() {
        testSubject.appendEvents(createEvent(0));
        entityManager.flush();
        entityManager.clear();
        testSubject.appendEvents(createEvent(0));
    }

    @DirtiesContext
    @Test
    @Transactional
    public void testStoreDuplicateEvent_NoSqlExceptionTranslator() {
        testSubject.setPersistenceExceptionResolver(null);
        testSubject.appendEvents(createEvent(0));
        entityManager.flush();
        entityManager.clear();
        try {
            testSubject.appendEvents(createEvent(0));
        } catch (ConcurrencyException ex) {
            fail("Didn't expect exception to be translated");
        } catch (Exception ex) {
            final StringWriter writer = new StringWriter();
            ex.printStackTrace(new PrintWriter(writer));
            assertTrue("Exception message doesn't mention 'DomainEventEntry': " + ex.getMessage(),
                       writer.toString().toLowerCase().contains("domainevententry"));
        }
    }

    @Test
    @Transactional
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testReadPartialStream() {
        testSubject.appendEvents(createEvents(5));
        entityManager.flush();
        entityManager.clear();
        assertEquals(4L, testSubject.readEvents(AGGREGATE, 2).reduce((a, b) -> b).get().getSequenceNumber());
        assertEquals(3L, testSubject.readEvents(AGGREGATE, 2).count());
    }

    @Transactional
    @Test
    @SuppressWarnings({"JpaQlInspection", "OptionalGetWithoutIsPresent"})
    public void testStoreEventsWithCustomEntity() throws Exception {
        testSubject = new JpaEventStorageEngine(entityManagerProvider) {

            @Override
            protected SerializedEventData<?> createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
                return new CustomDomainEventEntry((DomainEventMessage<?>) eventMessage, serializer);
            }

            @Override
            protected SerializedDomainEventData<?> createSnapshotEntity(DomainEventMessage<?> snapshot,
                                                                        Serializer serializer) {
                return new CustomSnapshotEventEntry(snapshot, serializer);
            }

            @Override
            protected String domainEventEntryEntityName() {
                return CustomDomainEventEntry.class.getSimpleName();
            }

            @Override
            protected String snapshotEventEntryEntityName() {
                return CustomSnapshotEventEntry.class.getSimpleName();
            }
        };

        testSubject.appendEvents(createEvent(AGGREGATE, 1, "Payload1"));
        testSubject.storeSnapshot(createEvent(AGGREGATE, 1, "Snapshot1"));
        entityManager.flush();
        entityManager.clear();

        assertFalse(entityManager.createQuery("SELECT e FROM CustomDomainEventEntry e").getResultList().isEmpty());
        assertEquals("Snapshot1", testSubject.readSnapshot(AGGREGATE).get().getPayload());
        assertEquals("Payload1", testSubject.readEvents(AGGREGATE).findFirst().get().getPayload());
    }

}
