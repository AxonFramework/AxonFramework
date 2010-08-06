/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Before
    public void setUp() {
        aggregate1 = new StubAggregateRoot();
        for (int t = 0; t < 10; t++) {
            aggregate1.changeState();
        }

        aggregate2 = new StubAggregateRoot();
        aggregate2.changeState();
        aggregate2.changeState();
        aggregate2.changeState();
        entityManager.createQuery("DELETE FROM DomainEventEntry").executeUpdate();
    }

    @Test
    public void testStoreAndLoadEvents() {
        assertNotNull(testSubject);
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        entityManager.flush();
        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     entityManager.createQuery("SELECT count(e) FROM DomainEventEntry e").getSingleResult());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents("test", aggregate2.getUncommittedEvents());
        entityManager.flush();
        entityManager.clear();

        DomainEventStream events = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEvent> actualEvents = new ArrayList<DomainEvent>();
        while (events.hasNext()) {
            DomainEvent event = events.next();
            actualEvents.add(event);
        }
        assertEquals(aggregate1.getUncommittedEventCount(), actualEvents.size());
    }

    @Test
    public void testLoadSegment() {
        assertNotNull(testSubject);
        // aggregate1 has 10 uncommitted events
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        entityManager.flush();
        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     entityManager.createQuery("SELECT count(e) FROM DomainEventEntry e").getSingleResult());

        DomainEventStream events = testSubject.readEventSegment("test", aggregate1.getIdentifier(), 4);
        while (events.hasNext()) {
            DomainEvent event = events.next();
            assertTrue(event.getSequenceNumber() >= 4);
        }
    }

    @Test
    public void testLoadSegment_NoCandidates() {
        assertNotNull(testSubject);
        // aggregate1 has 10 uncommitted events
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        entityManager.flush();
        assertEquals((long) aggregate1.getUncommittedEventCount(),
                     entityManager.createQuery("SELECT count(e) FROM DomainEventEntry e").getSingleResult());

        DomainEventStream events = testSubject.readEventSegment("test", aggregate1.getIdentifier(), 10);
        assertFalse(events.hasNext());
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
        List<DomainEvent> domainEvents = new ArrayList<DomainEvent>();
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
    public void testDoWithAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents("type1", new SimpleDomainEventStream(createDomainEvents(77)));
        testSubject.appendEvents("type2", new SimpleDomainEventStream(createDomainEvents(23)));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEvent.class));
    }

    private List<? extends DomainEvent> createDomainEvents(int numberOfEvents) {
        List<StubDomainEvent> events = new ArrayList<StubDomainEvent>();
        UUID aggregateIdentifier = UUID.randomUUID();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new StubDomainEvent(aggregateIdentifier, t));
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

        private StubStateChangedEvent(long sequenceNumber, UUID aggregateIdentifier) {
            super(sequenceNumber, aggregateIdentifier);
        }
    }
}
