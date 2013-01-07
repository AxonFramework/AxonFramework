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
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.upcasting.UpcastingContext;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.upcasting.UpcasterChain;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

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

    private MongoEventStore testSubject;
    private Mongo mongo;
    private DefaultMongoTemplate mongoTemplate;

    private StubAggregateRoot aggregate1;
    private StubAggregateRoot aggregate2;

    @Autowired
    private ApplicationContext context;

    @Before
    public void setUp() {
        try {
            mongo = context.getBean(Mongo.class);
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

    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(testSubject);
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        assertEquals((long) aggregate1.getUncommittedEventCount(), mongoTemplate.domainEventCollection().count());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents("test", aggregate2.getUncommittedEvents());
        DomainEventStream events = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<DomainEventMessage>();
        long expectedSequenceNumber = 0L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            actualEvents.add(event);
            assertEquals("Events are read back in in the wrong order",
                         expectedSequenceNumber,
                         event.getSequenceNumber());
            expectedSequenceNumber++;
        }
        assertEquals(aggregate1.getUncommittedEventCount(), actualEvents.size());
    }


    @DirtiesContext
    @Test
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

        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     mongoTemplate.domainEventCollection().count());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<Object>(aggregate2.getIdentifier(),
                                                      0,
                                                      new Object(),
                                                      Collections.singletonMap("key", (Object) "Value"))));

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
    public void testLoadWithSnapshotEvent() {
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        testSubject.appendSnapshotEvent("test", aggregate1.createSnapshotEvent());
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

    @Test
    public void testLoadWithMultipleSnapshotEvents() {
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        testSubject.appendSnapshotEvent("test", aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        testSubject.appendSnapshotEvent("test", aggregate1.createSnapshotEvent());
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
        testSubject.readEvents("test", UUID.randomUUID());
    }

    @Test
    public void testStoreDuplicateAggregate() {
        testSubject.appendEvents("type1", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>("aggregate1", 0, "payload")));
        try {
            testSubject.appendEvents("type1", new SimpleDomainEventStream(
                    new GenericDomainEventMessage<String>("aggregate1", 0, "payload")));
            fail("Expected exception to be thrown");
        } catch (ConcurrencyException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testVisitAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents("type1", new SimpleDomainEventStream(createDomainEvents(77)));
        testSubject.appendEvents("type2", new SimpleDomainEventStream(createDomainEvents(23)));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    public void testVisitEvents_AfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThan(onePM), eventVisitor);
        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(eventVisitor, times(13 + 14)).doWithEvent(captor.capture());
        assertEquals(new DateTime(2011, 12, 18, 14, 0, 0, 0), captor.getAllValues().get(0).getTimestamp());
        assertEquals(new DateTime(2011, 12, 18, 14, 0, 0, 1), captor.getAllValues().get(26).getTimestamp());
    }

    @Test
    public void testVisitEvents_BetweenTimestamps() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
        DateTime twoPM = new DateTime(2011, 12, 18, 14, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(twoPM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM)
                                               .and(criteriaBuilder.property("timeStamp").lessThanEquals(twoPM)),
                                eventVisitor);
        verify(eventVisitor, times(12 + 13)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    public void testVisitEvents_OnOrAfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM), eventVisitor);
        verify(eventVisitor, times(12 + 13 + 14)).doWithEvent(isA(DomainEventMessage.class));
    }


    private List<DomainEventMessage<StubStateChangedEvent>> createDomainEvents(int numberOfEvents) {
        List<DomainEventMessage<StubStateChangedEvent>> events = new ArrayList<DomainEventMessage<StubStateChangedEvent>>();
        final UUID aggregateIdentifier = UUID.randomUUID();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new GenericDomainEventMessage<StubStateChangedEvent>(
                    aggregateIdentifier, t, new StubStateChangedEvent(), null));
        }
        return events;
    }

    private static class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private final UUID identifier;

        private StubAggregateRoot() {
            this.identifier = UUID.randomUUID();
        }

        public void changeState() {
            apply(new StubStateChangedEvent());
        }

        @Override
        public UUID getIdentifier() {
            return identifier;
        }

        @EventHandler
        public void handleStateChange(StubStateChangedEvent event) {
        }

        public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
            return new GenericDomainEventMessage<StubStateChangedEvent>(
                    getIdentifier(), getVersion(), new StubStateChangedEvent(), null);
        }
    }

    private static class StubStateChangedEvent {

        private static final long serialVersionUID = 3459228620192273869L;

        private StubStateChangedEvent() {
        }
    }
}
