/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo3.eventstore;

import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.mongo3.mongoutils.MongoLauncher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.*;
import org.junit.runner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.ArrayList;
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
@ContextConfiguration(locations = {"classpath:META-INF/spring/mongo-context_dbobject.xml"})
public class MongoEventStoreTest_DBObjectSerialization {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStoreTest_DBObjectSerialization.class);
    private static MongodProcess mongod;
    private static MongodExecutable mongoExe;

    private MongoEventStore testSubject;
    private MongoClient mongo;
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
            mongo = context.getBean(MongoClient.class);
            testSubject = context.getBean(MongoEventStore.class);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        mongoTemplate = new DefaultMongoTemplate(mongo);
        String dbName = mongoTemplate.domainEventCollection().getNamespace().getDatabaseName();
        mongo.dropDatabase(dbName);

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
            // Tests AXON-169
            assertNotNull(event.getIdentifier());
            actualEvents.add(event);
            assertEquals("Events are read back in in the wrong order",
                         expectedSequenceNumber,
                         event.getSequenceNumber());
            expectedSequenceNumber++;
        }
        assertEquals(aggregate1.getUncommittedEventCount(), actualEvents.size());
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

    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        testSubject.readEvents("test", UUID.randomUUID());
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
        verify(eventVisitor, times(13 + 14)).doWithEvent(isA(DomainEventMessage.class));
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

        @EventSourcingHandler
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
