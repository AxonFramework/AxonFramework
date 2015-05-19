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

package org.axonframework.eventstore.jdbc;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.jpa.DomainEventEntry;
import org.axonframework.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.serializer.ChainingConverterFactory;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.axonframework.serializer.UnknownSerializedTypeException;
import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcasterChain;
import org.axonframework.upcasting.UpcastingContext;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:/META-INF/spring/db-context.xml",
        "classpath:/META-INF/spring/eventstore-jdbc-test-context.xml"})
public class JdbcEventStore_JpaBackedTest {

    @Autowired
    private JdbcEventStore testSubject;
    @PersistenceContext
    private EntityManager entityManager;
    private StubAggregateRoot aggregate1;
    private StubAggregateRoot aggregate2;

    @Autowired
    private PlatformTransactionManager txManager;
    private TransactionTemplate template;

    @Before
    public void setUp() {
        template = new TransactionTemplate(txManager);
        aggregate1 = new StubAggregateRoot(UUID.randomUUID());
        for (int t = 0; t < 10; t++) {
            aggregate1.changeState();
        }

        aggregate2 = new StubAggregateRoot();
        aggregate2.changeState();
        aggregate2.changeState();
        aggregate2.changeState();
        template.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                entityManager.createQuery("DELETE FROM DomainEventEntry").executeUpdate();
            }
        });
    }

    @After
    public void tearDown() {
        // just to make sure
//        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test(expected = DataIntegrityViolationException.class)
    public void testUniqueKeyConstraintOnEventIdentifier() {
        final SimpleSerializedObject<byte[]> emptySerializedObject = new SimpleSerializedObject<byte[]>(new byte[]{},
                                                                                                        byte[].class,
                                                                                                        "test",
                                                                                                        "");

        template.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(
                    TransactionStatus status) {
                DomainEventMessage firstEvent = aggregate2.getUncommittedEvents().next();
                entityManager.persist(new DomainEventEntry("type",
                                                           new GenericDomainEventMessage(
                                                                   "a",
                                                                   ZonedDateTime.now(),
                                                                   "someValue",
                                                                   0,
                                                                   "",
                                                                   MetaData.emptyInstance()),
                                                           emptySerializedObject,
                                                           emptySerializedObject));
            }
        });
        template.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(
                    TransactionStatus status) {
                entityManager.persist(new DomainEventEntry("type",
                                                           new GenericDomainEventMessage(
                                                                   "a",
                                                                   ZonedDateTime.now(),
                                                                   "anotherValue",
                                                                   0,
                                                                   "",
                                                                   MetaData.emptyInstance()),
                                                           emptySerializedObject,
                                                           emptySerializedObject));
            }
        });
    }

    @Transactional
    @Test(expected = IllegalArgumentException.class)
    public void testStoreAndLoadEvents_BadIdentifierType() {
        testSubject.appendEvents("type", new SimpleDomainEventStream(
                new GenericDomainEventMessage<Object>(new BadIdentifierType(), 1, new Object())));
    }

    @Transactional
    @Test(expected = UnknownSerializedTypeException.class)
    public void testUnknownSerializedTypeCausesException() {
        testSubject.appendEvents("type", aggregate1.getUncommittedEvents());
        entityManager.flush();
        entityManager.clear();
        entityManager.createQuery("UPDATE DomainEventEntry e SET e.payloadType = :type")
                     .setParameter("type", "unknown")
                     .executeUpdate();

        testSubject.readEvents("type", aggregate1.getIdentifier());
    }

    @Transactional
    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(testSubject);
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        entityManager.flush();
        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     entityManager.createQuery("SELECT count(e) FROM DomainEventEntry e").getSingleResult());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<Object>(aggregate2.getIdentifier(),
                                                      0,
                                                      new Object(),
                                                      Collections.singletonMap("key", (Object) "Value"))));
        entityManager.flush();
        entityManager.clear();

        DomainEventStream events = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<DomainEventMessage>();
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            event.getPayload();
            event.getMetaData();
            actualEvents.add(event);
        }
        assertEquals(aggregate1.getUncommittedEventCount(), actualEvents.size());

        /// we make sure persisted events have the same MetaData alteration logic
        DomainEventStream other = testSubject.readEvents("test", aggregate2.getIdentifier());
        assertTrue(other.hasNext());
        DomainEventMessage messageWithMetaData = other.next();
        DomainEventMessage altered = messageWithMetaData.withMetaData(Collections.singletonMap("key2",
                                                                                               (Object) "value"));
        DomainEventMessage combined = messageWithMetaData.andMetaData(Collections.singletonMap("key2",
                                                                                               (Object) "value"));
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
        assertNotNull(testSubject);
        UpcasterChain mockUpcasterChain = mock(UpcasterChain.class);
        when(mockUpcasterChain.upcast(isA(SerializedObject.class), isA(UpcastingContext.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        SerializedObject serializedObject = (SerializedObject) invocation.getArguments()[0];
                        return Arrays.asList(serializedObject, serializedObject);
                    }
                });

        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());

        testSubject.setUpcasterChain(mockUpcasterChain);
        entityManager.flush();
        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     entityManager.createQuery("SELECT count(e) FROM DomainEventEntry e").getSingleResult());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<Object>(aggregate2.getIdentifier(),
                                                      0,
                                                      new Object(),
                                                      Collections.singletonMap("key", (Object) "Value"))));
        entityManager.flush();
        entityManager.clear();

        DomainEventStream events = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<DomainEventMessage>();
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            event.getPayload();
            event.getMetaData();
            actualEvents.add(event);
        }

        assertEquals(20, actualEvents.size());
        for (int t = 0; t < 20; t = t + 2) {
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
    public void testLoad_LargeAmountOfEvents() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<String>(aggregateIdentifier, (long) t,
                                                                   "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents("test", new SimpleDomainEventStream(domainEvents));
        entityManager.flush();
        entityManager.clear();

        DomainEventStream events = testSubject.readEvents("test", aggregateIdentifier);
        long t = 0L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            assertEquals(t, event.getSequenceNumber());
            t++;
        }
        assertEquals(110L, t);
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
    public void testEntireStreamIsReadOnUnserializableSnapshot_WithException() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<String>(aggregateIdentifier, (long) t,
                                                                   "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents("test", new SimpleDomainEventStream(domainEvents));
        final Serializer serializer = new Serializer() {

            private ChainingConverterFactory converterFactory = new ChainingConverterFactory();

            @SuppressWarnings("unchecked")
            @Override
            public <T> SerializedObject<T> serialize(Object object, Class<T> expectedType) {
                Assert.assertEquals(byte[].class, expectedType);
                return new SimpleSerializedObject("this ain't gonna work".getBytes(), byte[].class, "failingType", "0");
            }

            @Override
            public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
                return byte[].class.equals(expectedRepresentation);
            }

            @Override
            public <S, T> T deserialize(SerializedObject<S> serializedObject) {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            public Class classForType(SerializedType type) {
                try {
                    return Class.forName(type.getName());
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }

            @Override
            public SerializedType typeForClass(Class type) {
                return new SimpleSerializedType(type.getName(), "");
            }

            @Override
            public ConverterFactory getConverterFactory() {
                return converterFactory;
            }
        };
        final DomainEventMessage<String> stubDomainEvent = new GenericDomainEventMessage<String>(
                aggregateIdentifier,
                (long) 30,
                "Mock contents", MetaData.emptyInstance()
        );
        SnapshotEventEntry entry = new SnapshotEventEntry(
                "test", stubDomainEvent,
                serializer.serialize(stubDomainEvent.getPayload(), byte[].class),
                serializer.serialize(stubDomainEvent.getMetaData(), byte[].class));
        entityManager.persist(entry);
        entityManager.flush();
        entityManager.clear();

        DomainEventStream stream = testSubject.readEvents("test", aggregateIdentifier);
        assertEquals(0L, stream.peek().getSequenceNumber());
    }

    @Test
    @Transactional
    public void testEntireStreamIsReadOnUnserializableSnapshot_WithError() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<String>(aggregateIdentifier, (long) t,
                                                                   "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents("test", new SimpleDomainEventStream(domainEvents));
        final Serializer serializer = new Serializer() {

            private ConverterFactory converterFactory = new ChainingConverterFactory();

            @SuppressWarnings("unchecked")
            @Override
            public <T> SerializedObject<T> serialize(Object object, Class<T> expectedType) {
                // this will cause InstantiationError, since it is an interface
                Assert.assertEquals(byte[].class, expectedType);
                return new SimpleSerializedObject("<org.axonframework.eventhandling.EventListener />".getBytes(),
                                                  byte[].class,
                                                  "failingType",
                                                  "0");
            }

            @Override
            public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
                return byte[].class.equals(expectedRepresentation);
            }

            @Override
            public <S, T> T deserialize(SerializedObject<S> serializedObject) {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            public Class classForType(SerializedType type) {
                try {
                    return Class.forName(type.getName());
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }

            @Override
            public SerializedType typeForClass(Class type) {
                return new SimpleSerializedType(type.getName(), "");
            }

            @Override
            public ConverterFactory getConverterFactory() {
                return converterFactory;
            }
        };
        final DomainEventMessage<String> stubDomainEvent = new GenericDomainEventMessage<String>(
                aggregateIdentifier,
                (long) 30,
                "Mock contents", MetaData.emptyInstance()
        );
        SnapshotEventEntry entry = new SnapshotEventEntry(
                "test", stubDomainEvent,
                serializer.serialize(stubDomainEvent.getPayload(), byte[].class),
                serializer.serialize(stubDomainEvent.getMetaData(), byte[].class));
        entityManager.persist(entry);
        entityManager.flush();
        entityManager.clear();

        DomainEventStream stream = testSubject.readEvents("test", aggregateIdentifier);
        assertEquals(0L, stream.peek().getSequenceNumber());
    }

    @Test
    @Transactional
    public void testLoad_LargeAmountOfEventsWithSnapshot() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<String>(aggregateIdentifier, (long) t,
                                                                   "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents("test", new SimpleDomainEventStream(domainEvents));
        testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 30,
                                                                                      "Mock contents",
                                                                                      MetaData.emptyInstance()
        ));
        entityManager.flush();
        entityManager.clear();

        DomainEventStream events = testSubject.readEvents("test", aggregateIdentifier);
        long t = 30L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            assertEquals(t, event.getSequenceNumber());
            t++;
        }
        assertEquals(110L, t);
    }

    @Test
    @Transactional
    public void testLoadWithSnapshotEvent() {
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        entityManager.flush();
        entityManager.clear();
        testSubject.appendSnapshotEvent("test", aggregate1.createSnapshotEvent());
        entityManager.flush();
        entityManager.clear();
        aggregate1.changeState();
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();

        DomainEventStream actualEventStream = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<DomainEventMessage>();
        while (actualEventStream.hasNext()) {
            DomainEventMessage next = actualEventStream.next();
            domainEvents.add(next);
            assertEquals(aggregate1.getIdentifier(), next.getAggregateIdentifier());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test(expected = EventStreamNotFoundException.class)
    @Transactional
    public void testLoadNonExistent() {
        testSubject.readEvents("Stub", UUID.randomUUID());
    }

    @Test
    @Transactional
    public void testVisitAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(77)));
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(23)));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    @Transactional
    public void testVisitAllEvents_IncludesUnknownEventType() throws Exception {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(10)));
        final GenericDomainEventMessage eventMessage = new GenericDomainEventMessage<String>("test", 0, "test");
        testSubject.appendEvents("test", new SimpleDomainEventStream(eventMessage));
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(10)));
        // we upcast the event to two instances, one of which is an unknown class
        testSubject.setUpcasterChain(new LazyUpcasterChain(Arrays.<Upcaster>asList(new StubUpcaster())));
        testSubject.visitEvents(eventVisitor);

        verify(eventVisitor, times(21)).doWithEvent(isA(DomainEventMessage.class));
    }

//    @Test
//    @Transactional
//    public void testVisitEvents_AfterTimestamp() {
//        EventVisitor eventVisitor = mock(EventVisitor.class);
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
//        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
//        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
//        DateTimeUtils.setCurrentMillisSystem();
//
//        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
//        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThan(onePM), eventVisitor);
//        verify(eventVisitor, times(13 + 14)).doWithEvent(isA(DomainEventMessage.class));
//    }

//    @Test
//    @Transactional
//    public void testVisitEvents_BetweenTimestamps() {
//        EventVisitor eventVisitor = mock(EventVisitor.class);
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
//        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
//        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
//        DateTime twoPM = new DateTime(2011, 12, 18, 14, 0, 0, 0);
//        DateTimeUtils.setCurrentMillisFixed(twoPM.getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
//        DateTimeUtils.setCurrentMillisSystem();
//
//        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
//        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM)
//                                               .and(criteriaBuilder.property("timeStamp").lessThanEquals(twoPM)),
//                                eventVisitor);
//        verify(eventVisitor, times(12 + 13)).doWithEvent(isA(DomainEventMessage.class));
//    }

//    @Test
//    @Transactional
//    public void testVisitEvents_OnOrAfterTimestamp() {
//        EventVisitor eventVisitor = mock(EventVisitor.class);
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
//        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
//        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
//        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
//        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
//        DateTimeUtils.setCurrentMillisSystem();
//
//        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
//        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM), eventVisitor);
//        verify(eventVisitor, times(12 + 13 + 14)).doWithEvent(isA(DomainEventMessage.class));
//    }

    @Test(expected = ConcurrencyException.class)
    @Transactional
    public void testStoreDuplicateEvent_WithSqlExceptionTranslator() {
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>("123", 0L,
                                                      "Mock contents", MetaData.emptyInstance())));
        entityManager.flush();
        entityManager.clear();
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>("123", 0L,
                                                      "Mock contents", MetaData.emptyInstance())));
    }

    @DirtiesContext
    @Test
    @Transactional
    public void testStoreDuplicateEvent_NoSqlExceptionTranslator() {
        testSubject.setPersistenceExceptionResolver(null);
        try {
            testSubject.appendEvents("test", new SimpleDomainEventStream(
                    new GenericDomainEventMessage<String>("123", (long) 0,
                                                          "Mock contents", MetaData.emptyInstance())));
            entityManager.flush();
            entityManager.clear();
            testSubject.appendEvents("test", new SimpleDomainEventStream(
                    new GenericDomainEventMessage<String>("123", (long) 0,
                                                          "Mock contents", MetaData.emptyInstance())));
        } catch (ConcurrencyException ex) {
            fail("Didn't expect exception to be translated");
        } catch (Exception ex) {
            final StringWriter writer = new StringWriter();
            ex.printStackTrace(new PrintWriter(writer));
            assertTrue("Got the right exception, "
                               + "but the message doesn't seem to mention 'DomainEventEntry': " + ex.getMessage(),
                       writer.toString().toLowerCase().contains("domainevententry"));
        }
    }

    @DirtiesContext
    @Test
    @Transactional
    public void testPrunesSnaphotsWhenNumberOfSnapshotsExceedsConfiguredMaxSnapshotsArchived() {
        testSubject.setMaxSnapshotsArchived(1);

        StubAggregateRoot aggregate = new StubAggregateRoot();

        aggregate.changeState();
        testSubject.appendEvents("type", aggregate.getUncommittedEvents());
        aggregate.commitEvents();
        entityManager.flush();
        entityManager.clear();

        testSubject.appendSnapshotEvent("type", aggregate.createSnapshotEvent());
        entityManager.flush();
        entityManager.clear();

        aggregate.changeState();
        testSubject.appendEvents("type", aggregate.getUncommittedEvents());
        aggregate.commitEvents();
        entityManager.flush();
        entityManager.clear();

        testSubject.appendSnapshotEvent("type", aggregate.createSnapshotEvent());
        entityManager.flush();
        entityManager.clear();

        @SuppressWarnings({"unchecked"})
        List<SnapshotEventEntry> snapshots =
                entityManager.createQuery("SELECT e FROM SnapshotEventEntry e "
                                                  + "WHERE e.type = 'type' "
                                                  + "AND e.aggregateIdentifier = :aggregateIdentifier")
                             .setParameter("aggregateIdentifier", aggregate.getIdentifier().toString())
                             .getResultList();
        assertEquals("archived snapshot count", 1L, snapshots.size());
        assertEquals("archived snapshot sequence", 1L, snapshots.iterator().next().getSequenceNumber());
    }

    @Test
    @Transactional
    public void testReadPartialStream_WithoutEnd() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 4,
                                                      "Mock contents", MetaData.emptyInstance())));
        testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>(aggregateIdentifier,
                                                                                      (long) 3,
                                                                                      "Mock contents",
                                                                                      MetaData.emptyInstance()));

        entityManager.flush();
        entityManager.clear();

        DomainEventStream actual = testSubject.readEvents("test", aggregateIdentifier, 2);
        for (int i=2;i<=4;i++) {
            assertTrue(actual.hasNext());
            assertEquals(i, actual.next().getSequenceNumber());
        }
        assertFalse(actual.hasNext());
    }

    @Test
    @Transactional
    public void testReadPartialStream_WithEnd() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 4,
                                                      "Mock contents", MetaData.emptyInstance())));

        testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>(aggregateIdentifier,
                                                                                      (long) 3,
                                                                                      "Mock contents",
                                                                                      MetaData.emptyInstance()));

        entityManager.flush();
        entityManager.clear();

        DomainEventStream actual = testSubject.readEvents("test", aggregateIdentifier, 2, 3);
        for (int i=2;i<=3;i++) {
            assertTrue(actual.hasNext());
            assertEquals(i, actual.next().getSequenceNumber());
        }
        assertFalse(actual.hasNext());
    }

    private SerializedObject<byte[]> mockSerializedObject(byte[] bytes) {
        return new SimpleSerializedObject<byte[]>(bytes, byte[].class, "java.lang.String", "0");
    }

    private List<DomainEventMessage<StubStateChangedEvent>> createDomainEvents(int numberOfEvents) {
        List<DomainEventMessage<StubStateChangedEvent>> events = new ArrayList<DomainEventMessage<StubStateChangedEvent>>();
        final Object aggregateIdentifier = UUID.randomUUID();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new GenericDomainEventMessage<StubStateChangedEvent>(
                    aggregateIdentifier,
                    t,
                    new StubStateChangedEvent(), MetaData.emptyInstance()
            ));
        }
        return events;
    }

    private static class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private static final long serialVersionUID = -3656612830058057848L;
        private final Object identifier;

        private StubAggregateRoot() {
            this(UUID.randomUUID());
        }

        private StubAggregateRoot(Object identifier) {
            this.identifier = identifier;
        }

        public void changeState() {
            apply(new StubStateChangedEvent());
        }

        @Override
        public Object getIdentifier() {
            return identifier;
        }

        @EventSourcingHandler
        public void handleStateChange(StubStateChangedEvent event) {
        }

        public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
            return new GenericDomainEventMessage<StubStateChangedEvent>(getIdentifier(), getVersion(),
                                                                        new StubStateChangedEvent(),
                                                                        MetaData.emptyInstance()
            );
        }
    }

    private static class StubStateChangedEvent {

        private StubStateChangedEvent() {
        }
    }

    private static class BadIdentifierType {

    }

    private static class StubUpcaster implements Upcaster<byte[]> {

        @Override
        public boolean canUpcast(SerializedType serializedType) {
            return "java.lang.String".equals(serializedType.getName());
        }

        @Override
        public Class<byte[]> expectedRepresentationType() {
            return byte[].class;
        }

        @Override
        public List<SerializedObject<?>> upcast(SerializedObject<byte[]> intermediateRepresentation,
                                                List<SerializedType> expectedTypes, UpcastingContext context) {
            return Arrays.<SerializedObject<?>>asList(
                    new SimpleSerializedObject<String>("data1", String.class, expectedTypes.get(0)),
                    new SimpleSerializedObject<byte[]>(intermediateRepresentation.getData(), byte[].class,
                                                       expectedTypes.get(1)));
        }

        @Override
        public List<SerializedType> upcast(SerializedType serializedType) {
            return Arrays.<SerializedType>asList(new SimpleSerializedType("unknownType1", "2"),
                                                 new SimpleSerializedType(StubStateChangedEvent.class.getName(), "2"));
        }
    }
}
