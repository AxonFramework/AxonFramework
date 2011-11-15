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

package org.axonframework.eventstore.mongo;

import com.mongodb.Mongo;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.junit.*;
import org.junit.runner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

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

    private MongoEventStore eventStore;
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
            eventStore = context.getBean(MongoEventStore.class);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        mongoTemplate = new DefaultMongoTemplate(mongo);
        mongoTemplate.database().dropDatabase();
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
        assertNotNull(eventStore);
        eventStore.appendEvents("test", aggregate1.getUncommittedEvents());
        assertEquals((long) aggregate1.getUncommittedEventCount(), mongoTemplate.domainEventCollection().count());

        // we store some more events to make sure only correct events are retrieved
        eventStore.appendEvents("test", aggregate2.getUncommittedEvents());
        DomainEventStream events = eventStore.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<DomainEventMessage>();
        Long expectedSequenceNumber = 0L;
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

    @Test
    public void testLoadWithSnapshotEvent() {
        eventStore.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        eventStore.appendSnapshotEvent("test", aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        eventStore.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();

        DomainEventStream actualEventStream = eventStore.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<DomainEventMessage>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        eventStore.readEvents("test", new UUIDAggregateIdentifier());
    }

    @Test
    public void testDoWithAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        eventStore.appendEvents("type1", new SimpleDomainEventStream(createDomainEvents(77)));
        eventStore.appendEvents("type2", new SimpleDomainEventStream(createDomainEvents(23)));

        eventStore.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

    private List<DomainEventMessage<StubStateChangedEvent>> createDomainEvents(int numberOfEvents) {
        List<DomainEventMessage<StubStateChangedEvent>> events = new ArrayList<DomainEventMessage<StubStateChangedEvent>>();
        final AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new GenericDomainEventMessage<StubStateChangedEvent>(
                    aggregateIdentifier, t, null, new StubStateChangedEvent()));
        }
        return events;
    }

    private static class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

        public void changeState() {
            apply(new StubStateChangedEvent());
        }

        @EventHandler
        public void handleStateChange(StubStateChangedEvent event) {
        }

        public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
            return new GenericDomainEventMessage<StubStateChangedEvent>(
                    getIdentifier(), getVersion(), null, new StubStateChangedEvent());
        }
    }

    private static class StubStateChangedEvent {

        private static final long serialVersionUID = 3459228620192273869L;

        private StubStateChangedEvent() {
        }
    }
}
