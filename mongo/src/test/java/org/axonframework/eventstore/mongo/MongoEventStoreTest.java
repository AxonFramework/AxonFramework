/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventstore.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.Mongo;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.mongoutils.MongoLauncher;
import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcasterChain;
import org.axonframework.upcasting.UpcastingContext;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>Beware with this test, it requires a running mongodb as specified in the configuration file, if no mongo instance
 * is running, tests will be ignored.</p> <p/> <p>Autowired dependencies are left out on purpose, it does not work with
 * the assume used to check if mongo is running.</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:META-INF/spring/mongo-context.xml"})
public class MongoEventStoreTest {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStoreTest.class);

    private static MongodExecutable mongoExe;
    private static MongodProcess mongod;

    private MongoEventStore testSubject;
    private DefaultMongoTemplate mongoTemplate;

    private StubAggregateRoot aggregate1;
    private StubAggregateRoot aggregate2;

    @Autowired
    private ApplicationContext context;

    @BeforeClass
    public static void start() throws IOException {
        mongoExe = MongoLauncher.prepareExecutable();
        mongod = mongoExe.start();
    }

    @AfterClass
    public static void shutdown() {
        if (mongod != null) {
            mongod.stop();
        }
        if (mongoExe != null) {
            mongoExe.stop();
        }
    }

    @Before
    public void setUp() {
        try {
            Mongo mongo = context.getBean(Mongo.class);
            testSubject = context.getBean(MongoEventStore.class);
            mongoTemplate = new DefaultMongoTemplate(mongo);
            mongoTemplate.domainEventCollection().remove(new BasicDBObject());
            mongoTemplate.snapshotEventCollection().remove(new BasicDBObject());
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        aggregate1 = new StubAggregateRoot();
        for (int t = 0; t < 10; t++) {
            aggregate1.changeState();
        }

        aggregate2 = new StubAggregateRoot();
        aggregate2.changeState();
        aggregate2.changeState();
        aggregate2.changeState();
    }

    @DirtiesContext
    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(testSubject);
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        assertEquals((long) aggregate1.getRegisteredEventCount(), mongoTemplate.domainEventCollection().count());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents(aggregate2.getRegisteredEvents());
        DomainEventStream events = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<>();
        long expectedSequenceNumber = 0L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            actualEvents.add(event);
            assertEquals("Events are read back in in the wrong order",
                         expectedSequenceNumber,
                         event.getSequenceNumber());
            expectedSequenceNumber++;
        }
        assertEquals(aggregate1.getRegisteredEventCount(), actualEvents.size());
    }


    @DirtiesContext
    @Test
    public void testStoreAndLoadEvents_WithUpcaster() {
        assertNotNull(testSubject);
        UpcasterChain mockUpcasterChain = mock(UpcasterChain.class);
        when(mockUpcasterChain.upcast(isA(SerializedObject.class), isA(UpcastingContext.class)))
                .thenAnswer(invocation -> {
                    SerializedObject serializedObject = (SerializedObject) invocation.getArguments()[0];
                    return asList(serializedObject, serializedObject);
                });

        testSubject.appendEvents(aggregate1.getRegisteredEvents());

        testSubject.setUpcasterChain(mockUpcasterChain);

        assertEquals((long) aggregate1.getRegisteredEventCount(),
                     mongoTemplate.domainEventCollection().count());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents(asList(
                new GenericDomainEventMessage<>(aggregate2.getIdentifier(),
                                                0,
                                                new Object(),
                                                Collections.singletonMap("key", (Object) "Value"))));

        DomainEventStream events = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<>();
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

    @DirtiesContext
    @Test
    public void testLoadWithSnapshotEvent() {
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.reset();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.reset();

        DomainEventStream actualEventStream = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @DirtiesContext
    @Test
    public void testLoadPartiallyWithSnapshotEvent() {
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.reset();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.reset();

        DomainEventStream actualEventStream = testSubject.readEvents(aggregate1.getIdentifier(), 3);
        List<DomainEventMessage> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(8, domainEvents.size());
        assertEquals(3, domainEvents.get(0).getSequenceNumber());
    }

    @DirtiesContext
    @Test
    public void testLoadPartiallyWithEndWithSnapshotEvent() {
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.reset();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.reset();

        DomainEventStream actualEventStream = testSubject.readEvents(aggregate1.getIdentifier(), 3, 6);
        List<DomainEventMessage> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(4, domainEvents.size());
        assertEquals(3, domainEvents.get(0).getSequenceNumber());
    }

    @DirtiesContext
    @Test
    public void testLoadWithMultipleSnapshotEvents() {
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.getRegisteredEvents().clear();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.getRegisteredEvents().clear();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        aggregate1.getRegisteredEvents().clear();

        DomainEventStream actualEventStream = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test
    public void testInsertDuplicateSnapshot() throws Exception {
        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>("id1", 1, "test"));
        try {
            testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>("id1", 1, "test"));
            fail("Expected concurrency exception");
        } catch (ConcurrencyException e) {
            assertTrue(e.getMessage().contains("Snapshot"));
        }
    }

    @DirtiesContext
    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        testSubject.readEvents(UUID.randomUUID().toString());
    }

    @DirtiesContext
    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadStream_UpcasterClearsAllFound() {
        testSubject.setUpcasterChain((serializedObject, upcastingContext) -> Collections.emptyList());
        final String streamId = UUID.randomUUID().toString();
        testSubject.appendEvents(asList(new GenericDomainEventMessage<>(streamId, 0, "test")));
        testSubject.readEvents(streamId);
    }

    @DirtiesContext
    @Test
    public void testStoreDuplicateAggregate() {
        testSubject.appendEvents(asList(new GenericDomainEventMessage<>("aggregate1", 0, "payload")));
        try {
            testSubject.appendEvents(asList(new GenericDomainEventMessage<>("aggregate1", 0, "payload")));
            fail("Expected exception to be thrown");
        } catch (ConcurrencyException e) {
            assertNotNull(e);
        }
    }

    @DirtiesContext
    @Test
    public void testVisitAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents(createDomainEvents(77));
        testSubject.appendEvents(createDomainEvents(23));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

    @DirtiesContext
    @Test
    public void testVisitAllEvents_IncludesUnknownEventType() throws Exception {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents(createDomainEvents(10));
        final GenericDomainEventMessage eventMessage = new GenericDomainEventMessage<>("test", 0, "test");
        testSubject.appendEvents(asList(eventMessage));
        testSubject.appendEvents(createDomainEvents(10));
        // we upcast the event to two instances, one of which is an unknown class
        testSubject.setUpcasterChain(new LazyUpcasterChain(Arrays.<Upcaster>asList(new StubUpcaster())));
        testSubject.visitEvents(eventVisitor);

        verify(eventVisitor, times(21)).doWithEvent(isA(DomainEventMessage.class));
    }

    @DirtiesContext
    @Test
    public void testVisitEvents_AfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        setClock(ZonedDateTime.of(2011, 12, 18, 12, 59, 59, 999000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(11));
        ZonedDateTime onePM = ZonedDateTime.of(2011, 12, 18, 13, 0, 0, 0, ZoneOffset.UTC);
        setClock(onePM);
        testSubject.appendEvents(createDomainEvents(12));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 0, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(13));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 1000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(14));
        setClock(Clock.systemDefaultZone());

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThan(onePM), eventVisitor);
        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(eventVisitor, times(13 + 14)).doWithEvent(captor.capture());
        assertEquals(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 0, ZoneOffset.UTC).toInstant(), captor.getAllValues().get(0).getTimestamp());
        assertEquals(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 1000000, ZoneOffset.UTC).toInstant(), captor.getAllValues().get(26).getTimestamp());
    }

    @DirtiesContext
    @Test
    public void testVisitEvents_BetweenTimestamps() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        setClock(ZonedDateTime.of(2011, 12, 18, 12, 59, 59, 999000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(11));
        ZonedDateTime onePM = ZonedDateTime.of(2011, 12, 18, 13, 0, 0, 0, ZoneOffset.UTC);
        setClock(onePM);
        testSubject.appendEvents(createDomainEvents(12));
        ZonedDateTime twoPM = ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 0, ZoneOffset.UTC);
        setClock(twoPM);
        testSubject.appendEvents(createDomainEvents(13));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 1000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(14));
        setClock(Clock.systemDefaultZone());

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM)
                                               .and(criteriaBuilder.property("timeStamp").lessThanEquals(twoPM)),
                                eventVisitor);
        verify(eventVisitor, times(12 + 13)).doWithEvent(isA(DomainEventMessage.class));
    }

    @DirtiesContext
    @Test
    public void testVisitEvents_OnOrAfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        setClock(ZonedDateTime.of(2011, 12, 18, 12, 59, 59, 999000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(11));
        ZonedDateTime onePM = ZonedDateTime.of(2011, 12, 18, 13, 0, 0, 0, ZoneOffset.UTC);
        setClock(onePM);
        testSubject.appendEvents(createDomainEvents(12));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 0, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(13));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 1000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(14));
        setClock(Clock.systemDefaultZone());

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM), eventVisitor);
        verify(eventVisitor, times(12 + 13 + 14)).doWithEvent(isA(DomainEventMessage.class));
    }


    private List<DomainEventMessage<?>> createDomainEvents(int numberOfEvents) {
        List<DomainEventMessage<?>> events = new ArrayList<>(numberOfEvents);
        final String aggregateIdentifier = UUID.randomUUID().toString();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new GenericDomainEventMessage<>(
                    aggregateIdentifier, t, new StubStateChangedEvent(), null));
        }
        return events;
    }

    private void setClock(ZonedDateTime zonedDateTime) {
        setClock(Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.getZone()));
    }
    private void setClock(Clock clock) {
        GenericEventMessage.clock = clock;
    }

}
