package org.axonframework.eventstore.gae;

import com.google.appengine.api.datastore.*;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.SnapshotEventStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Jettro Coenradie
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext-eventstore-test.xml"})
public class GaeEventStoreTest {
    private final LocalServiceTestHelper helper =
            new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

    private SnapshotEventStore eventStore;
    private DatastoreService datastoreService;

    private StubAggregateRoot aggregate1;
    private StubAggregateRoot aggregate2;

    @Autowired
    private ApplicationContext context;

    @Before
    public void setUp() throws Exception {
        helper.setUp();
        eventStore = context.getBean(SnapshotEventStore.class);
        datastoreService = DatastoreServiceFactory.getDatastoreService();

        aggregate1 = new StubAggregateRoot();
        for (int t = 0; t < 10; t++) {
            aggregate1.changeState();
        }

        aggregate2 = new StubAggregateRoot();
        aggregate2.changeState();
        aggregate2.changeState();
        aggregate2.changeState();

    }

    @After
    public void tearDown() throws Exception {
        helper.tearDown();
    }

    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(eventStore);
        eventStore.appendEvents("test", aggregate1.getUncommittedEvents());
        Query query = createCountQuery("test");
        PreparedQuery preparedQuery = datastoreService.prepare(query);

        assertEquals((long) aggregate1.getUncommittedEventCount(), preparedQuery.countEntities(FetchOptions.Builder.withDefaults()));

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
        eventStore.readEvents("test", new UUIDAggregateIdentifier());
    }

    private Query createCountQuery(String type) {
        return new Query(type);
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
