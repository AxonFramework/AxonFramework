package org.axonframework.eventstore.mongo;

import org.axonframework.domain.*;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * Beware with this test, it requires a running mongodb as specified in the configuration file
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:META-INF/spring/mongo-context.xml"})
@Ignore("Requires a running Mongo instance, therefore this test is disabled for now")
public class MongoEventStoreTest {

    @Autowired
    private MongoEventStore eventStore;

    @Autowired
    private MongoHelper helper;

    private StubAggregateRoot aggregate1;
    private StubAggregateRoot aggregate2;


    @Before
    public void setUp() {
        helper.database().dropDatabase();
        aggregate1 = new StubAggregateRoot();
        for (int t = 0; t < 10; t++) {
            aggregate1.changeState();
        }

        aggregate2 = new StubAggregateRoot();
        aggregate2.changeState();
        aggregate2.changeState();
        aggregate2.changeState();
    }

    @BeforeClass
    public static void checkProductionMongoFactory() {
        System.setProperty("test.context", "true");
    }

    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(eventStore);
        eventStore.appendEvents("test", aggregate1.getUncommittedEvents());
        assertEquals((long) aggregate1.getUncommittedEventCount(), helper.domainEvents().count());

        // we store some more events to make sure only correct events are retrieved
        eventStore.appendEvents("test", aggregate2.getUncommittedEvents());

        DomainEventStream events = eventStore.readEvents("test", aggregate1.getIdentifier());
        List<DomainEvent> actualEvents = new ArrayList<DomainEvent>();
        while (events.hasNext()) {
            DomainEvent event = events.next();
            actualEvents.add(event);
        }
        assertEquals(aggregate1.getUncommittedEventCount(), actualEvents.size());
    }

    @Test
    public void testLoadSegment() {
        assertNotNull(eventStore);
        // aggregate1 has 10 uncommitted events
        eventStore.appendEvents("test", aggregate1.getUncommittedEvents());
        assertEquals((long) aggregate1.getUncommittedEventCount(), helper.domainEvents().count());

        DomainEventStream events = eventStore.readEventSegment("test", aggregate1.getIdentifier(), 4);
        while (events.hasNext()) {
            DomainEvent event = events.next();
            assertTrue(event.getSequenceNumber() >= 4);
        }
    }

    @Test
    public void testLoadSegment_NoCandidates() {
        assertNotNull(eventStore);
        // aggregate1 has 10 uncommitted events
        eventStore.appendEvents("test", aggregate1.getUncommittedEvents());
        assertEquals((long) aggregate1.getUncommittedEventCount(), helper.domainEvents().count());

        DomainEventStream events = eventStore.readEventSegment("test", aggregate1.getIdentifier(), 10);
        assertFalse(events.hasNext());
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
        List<DomainEvent> domainEvents = new ArrayList<DomainEvent>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        eventStore.readEvents("test", AggregateIdentifierFactory.randomIdentifier());
    }

    @Test
    public void testDoWithAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        eventStore.appendEvents("type1", new SimpleDomainEventStream(createDomainEvents(77)));
        eventStore.appendEvents("type2", new SimpleDomainEventStream(createDomainEvents(23)));

        eventStore.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEvent.class));
    }


    private List<StubStateChangedEvent> createDomainEvents(int numberOfEvents) {
        List<StubStateChangedEvent> events = new ArrayList<StubStateChangedEvent>();
        final AggregateIdentifier aggregateIdentifier = AggregateIdentifierFactory.randomIdentifier();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new StubStateChangedEvent(t, aggregateIdentifier));
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

        public DomainEvent createSnapshotEvent() {
            return new StubStateChangedEvent(getVersion(), getIdentifier());
        }
    }

    private static class StubStateChangedEvent extends DomainEvent {

        private static final long serialVersionUID = 3459228620192273869L;

        private StubStateChangedEvent() {
        }

        private StubStateChangedEvent(long sequenceNumber, AggregateIdentifier aggregateIdentifier) {
            super(sequenceNumber, aggregateIdentifier);
        }
    }

}