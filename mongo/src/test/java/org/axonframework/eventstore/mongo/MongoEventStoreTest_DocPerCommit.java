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

import com.mongodb.Mongo;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.mongoutils.MongoLauncher;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcasterChain;
import org.axonframework.upcasting.UpcastingContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
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
@ContextConfiguration(locations = {"classpath:META-INF/spring/mongo-context_doc_per_commit.xml"})
public class MongoEventStoreTest_DocPerCommit {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStoreTest_DocPerCommit.class);
    private static MongodProcess mongod;
    private static MongodExecutable mongoExe;

    private MongoEventStore testSubject;
    private Mongo mongo;
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
            mongo = context.getBean(Mongo.class);
            testSubject = context.getBean(MongoEventStore.class);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        mongoTemplate = new DefaultMongoTemplate(mongo);
        mongoTemplate.domainEventCollection().getDB().dropDatabase();
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
    public void testStoreEmptyUncommittedEventList(){
        assertNotNull(testSubject);
        StubAggregateRoot aggregate = new StubAggregateRoot();
        // no events
        assertEquals(0, aggregate.getUncommittedEventCount());
        testSubject.appendEvents(aggregate.getUncommittedEvents());

        assertEquals(0, mongoTemplate.domainEventCollection().count());
    }

    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(testSubject);
        testSubject.appendEvents(aggregate1.getUncommittedEvents());
        // just one commit
        assertEquals(1, mongoTemplate.domainEventCollection().count());
        // with multiple events
        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     ((List) mongoTemplate.domainEventCollection().findOne().get("events")).size());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents(aggregate2.getUncommittedEvents());
        DomainEventStream events = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<>();
        long expectedSequenceNumber = 0L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            actualEvents.add(event);
            // Tests AXON-169
            assertNotNull(event.getIdentifier());
            assertEquals("Events are read back in the wrong order",
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
                .thenAnswer(invocation -> {
                    SerializedObject serializedObject = (SerializedObject) invocation.getArguments()[0];
                    return asList(serializedObject, serializedObject);
                });

        testSubject.appendEvents(aggregate1.getUncommittedEvents());

        testSubject.setUpcasterChain(mockUpcasterChain);

        // just one commit
        assertEquals(1, mongoTemplate.domainEventCollection().count());
        // with multiple events
        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     ((List) mongoTemplate.domainEventCollection().findOne().get("events")).size());

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

    @Test
    public void testLoadWithSnapshotEvent() {
        testSubject.appendEvents(aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();

        DomainEventStream actualEventStream = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test
    public void testLoadWithMultipleSnapshotEvents() {
        testSubject.appendEvents(aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();

        DomainEventStream actualEventStream = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        testSubject.readEvents(UUID.randomUUID().toString());
    }

    @Test
    public void testVisitAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents(createDomainEvents(77));
        testSubject.appendEvents(createDomainEvents(23));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

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


    @Test
    public void testVisitEvents_AfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents(createDomainEvents(11));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents(createDomainEvents(12));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
        testSubject.appendEvents(createDomainEvents(13));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents(createDomainEvents(14));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timestamp").greaterThan(onePM), eventVisitor);
        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(eventVisitor, times(13 + 14)).doWithEvent(captor.capture());
        assertEquals(new DateTime(2011, 12, 18, 14, 0, 0, 0), captor.getAllValues().get(0).getTimestamp());
        assertEquals(new DateTime(2011, 12, 18, 14, 0, 0, 1), captor.getAllValues().get(26).getTimestamp());
    }

    @Test
    public void testVisitEvents_BetweenTimestamps() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents(createDomainEvents(11));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents(createDomainEvents(12));
        DateTime twoPM = new DateTime(2011, 12, 18, 14, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(twoPM.getMillis());
        testSubject.appendEvents(createDomainEvents(13));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents(createDomainEvents(14));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timestamp").greaterThanEquals(onePM)
                                               .and(criteriaBuilder.property("timestamp").lessThanEquals(twoPM)),
                                eventVisitor);
        verify(eventVisitor, times(12 + 13)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    public void testVisitEvents_OnOrAfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents(createDomainEvents(11));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents(createDomainEvents(12));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
        testSubject.appendEvents(createDomainEvents(13));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents(createDomainEvents(14));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timestamp").greaterThanEquals(onePM), eventVisitor);
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

    private static class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private final UUID identifier;

        private StubAggregateRoot() {
            this.identifier = UUID.randomUUID();
        }

        public void changeState() {
            apply(new StubStateChangedEvent());
        }

        @Override
        public String getIdentifier() {
            return identifier.toString();
        }

        @EventSourcingHandler
        public void handleStateChange(StubStateChangedEvent event) {
        }

        public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
            return new GenericDomainEventMessage<>(
                    getIdentifier(), getVersion(), new StubStateChangedEvent(), null);
        }
    }

    private static class StubStateChangedEvent {

        private static final long serialVersionUID = 3459228620192273869L;

        private StubStateChangedEvent() {
        }
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
                    new SimpleSerializedObject<>("data1", String.class, expectedTypes.get(0)),
                    new SimpleSerializedObject<>(intermediateRepresentation.getData(), byte[].class,
                                                       expectedTypes.get(1)));
        }

        @Override
        public List<SerializedType> upcast(SerializedType serializedType) {
            return Arrays.<SerializedType>asList(new SimpleSerializedType("unknownType1", "2"),
                                                 new SimpleSerializedType(StubStateChangedEvent.class.getName(), "2"));
        }
    }
}
