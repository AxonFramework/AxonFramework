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

package org.axonframework.eventstore.gae;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.SnapshotEventStore;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

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

        assertEquals((long) aggregate1.getUncommittedEventCount(), preparedQuery.countEntities(FetchOptions.Builder
                                                                                                       .withDefaults()));

        // we store some more events to make sure only correct events are retrieved
        eventStore.appendEvents("test", aggregate2.getUncommittedEvents());

        DomainEventStream events = eventStore.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage<?>> actualEvents = new ArrayList<>();
        while (events.hasNext()) {
            DomainEventMessage<?> event = events.next();
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
        List<DomainEventMessage<? extends Object>> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            domainEvents.add(actualEventStream.next());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        eventStore.readEvents("test", UUID.randomUUID());
    }

    private Query createCountQuery(String type) {
        return new Query(type);
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
            return new GenericDomainEventMessage<>(
                    getIdentifier(),
                    getVersion(),
                    new StubStateChangedEvent());
        }
    }

    private static class StubStateChangedEvent {

        private static final long serialVersionUID = 3459228620192273869L;

        private StubStateChangedEvent() {
        }
    }
}
