/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:/META-INF/spring/db-context.xml",
        "classpath:/META-INF/spring/test-context.xml"})
@Transactional()
public class JpaEventStoreTest {

    @Autowired
    private JpaEventStore testSubject;

    @PersistenceContext
    private EntityManager entityManager;

    private StubAggregateRoot aggregate1;
    private StubAggregateRoot aggregate2;
    private AggregateIdentifier mockAggregateIdentifier;

    @Before
    public void setUp() {
        mockAggregateIdentifier = mock(AggregateIdentifier.class);
        when(mockAggregateIdentifier.asString()).thenReturn(UUID.randomUUID().toString());
        aggregate1 = new StubAggregateRoot(mockAggregateIdentifier);
        for (int t = 0; t < 10; t++) {
            aggregate1.changeState();
        }

        aggregate2 = new StubAggregateRoot();
        aggregate2.changeState();
        aggregate2.changeState();
        aggregate2.changeState();
        entityManager.createQuery("DELETE FROM DomainEventEntry").executeUpdate();
    }

    @After
    public void tearDown() {
    }

    @Test
    @Rollback(false)
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

    @Test
    public void testLoad_LargeAmountOfEvents() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        AggregateIdentifier aggregateIdentifier = new StringAggregateIdentifier("id");
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

    @Test
    public void testLoad_LargeAmountOfEventsInSmallBatches() {
        testSubject.setBatchSize(10);
        testLoad_LargeAmountOfEvents();
    }

    @Test
    public void testEntireStreamIsReadOnUnserializableSnapshot() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        AggregateIdentifier aggregateIdentifier = new StringAggregateIdentifier("id");
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<String>(aggregateIdentifier, (long) t,
                                                                   "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents("test", new SimpleDomainEventStream(domainEvents));
        final Serializer serializer = new Serializer() {
            @Override
            public SerializedType serialize(Object object, OutputStream outputStream) throws IOException {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            public SerializedObject serialize(Object object) {
                return new SimpleSerializedObject("this ain't gonna work".getBytes(), "failingType", 0);
            }

            @Override
            public Object deserialize(SerializedObject serializedObject) {
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
        };
        final DomainEventMessage<String> stubDomainEvent = new GenericDomainEventMessage<String>(
                aggregateIdentifier,
                (long) 30,
                "Mock contents", MetaData.emptyInstance()
        );
        SnapshotEventEntry entry = new SnapshotEventEntry("test",
                                                          stubDomainEvent,
                                                          serializer.serialize(stubDomainEvent.getPayload()),
                                                          serializer.serialize(stubDomainEvent.getMetaData()));
        entityManager.persist(entry);
        entityManager.flush();
        entityManager.clear();

        DomainEventStream stream = testSubject.readEvents("test", aggregateIdentifier);
        assertEquals(0L, stream.peek().getSequenceNumber());
    }

    @Test
    public void testLoad_LargeAmountOfEventsWithSnapshot() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        AggregateIdentifier aggregateIdentifier = new StringAggregateIdentifier("id");
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
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        testSubject.readEvents("Stub", new UUIDAggregateIdentifier());
    }

    @Test
    public void testDoWithAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(77, "type1")));
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(23, "type2")));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test(expected = ConcurrencyException.class)
    public void testStoreDuplicateEvent_WithSqlExceptionTranslator() {
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(new StringAggregateIdentifier("123"), 0L,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(new StringAggregateIdentifier("123"), 0L,
                                                      "Mock contents", MetaData.emptyInstance())));
    }

    @Test
    public void testStoreDuplicateEvent_NoSqlExceptionTranslator() {
        testSubject.setPersistenceExceptionResolver(null);
        try {
            testSubject.appendEvents("test", new SimpleDomainEventStream(
                    new GenericDomainEventMessage<String>(new StringAggregateIdentifier("123"), (long) 0,
                                                          "Mock contents", MetaData.emptyInstance()),
                    new GenericDomainEventMessage<String>(new StringAggregateIdentifier("123"), (long) 0,
                                                          "Mock contents", MetaData.emptyInstance())));
        } catch (ConcurrencyException ex) {
            fail("Didn't expect exception to be translated");
        } catch (PersistenceException ex) {
            assertTrue("Got the right exception, "
                               + "but the message doesn't seem to mention 'Constraint': " + ex.getMessage(),
                       ex.getMessage().contains("Constraint"));
        }
    }

    @Test
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
                             .setParameter("aggregateIdentifier", aggregate.getIdentifier().asString())
                             .getResultList();
        assertEquals("archived snapshot count", 1L, snapshots.size());
        assertEquals("archived snapshot sequence", 1L, snapshots.iterator().next().getSequenceNumber());
    }

    @SuppressWarnings({"PrimitiveArrayArgumentToVariableArgMethod", "unchecked"})
    @Test
    public void testCustomEventEntryStore() {
        EventEntryStore eventEntryStore = mock(EventEntryStore.class);
        testSubject = new JpaEventStore(new SimpleEntityManagerProvider(entityManager), eventEntryStore);
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(new UUIDAggregateIdentifier(), (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(new UUIDAggregateIdentifier(), (long) 0,
                                                      "Mock contents", MetaData.emptyInstance())));
        verify(eventEntryStore, times(2)).persistEvent(eq("test"), isA(DomainEventMessage.class),
                                                       Matchers.<SerializedObject>any(),
                                                       Matchers.<SerializedObject>any(), same(entityManager));

        reset(eventEntryStore);
        GenericDomainEventMessage<String> eventMessage = new GenericDomainEventMessage<String>(
                new UUIDAggregateIdentifier(), 0L, "Mock contents", MetaData.emptyInstance());
        when(eventEntryStore.fetchBatch(anyString(), any(AggregateIdentifier.class), anyInt(), anyInt(),
                                        any(EntityManager.class)))
                .thenReturn(new ArrayList(Arrays.asList(new DomainEventEntry(
                        "Mock", eventMessage,
                        mockSerializedObject("Mock contents".getBytes()),
                        mockSerializedObject("Mock contents".getBytes())))));
        when(eventEntryStore.loadLastSnapshotEvent(anyString(), any(AggregateIdentifier.class),
                                                   any(EntityManager.class)))
                .thenReturn(null);

        testSubject.readEvents("test", new StringAggregateIdentifier("1"));

        verify(eventEntryStore).fetchBatch("test", new StringAggregateIdentifier("1"), 0, 100, entityManager);
        verify(eventEntryStore).loadLastSnapshotEvent("test", new StringAggregateIdentifier("1"), entityManager);
    }

    private SerializedObject mockSerializedObject(byte[] bytes) {
        return new SimpleSerializedObject(bytes, "mock", 0);
    }

    private List<DomainEventMessage<StubStateChangedEvent>> createDomainEvents(int numberOfEvents,
                                                                               String aggregateType) {
        List<DomainEventMessage<StubStateChangedEvent>> events = new ArrayList<DomainEventMessage<StubStateChangedEvent>>();
        final AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
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

        private StubAggregateRoot() {
        }

        private StubAggregateRoot(AggregateIdentifier identifier) {
            super(identifier);
        }

        public void changeState() {
            apply(new StubStateChangedEvent());
        }

        @EventHandler
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
}
