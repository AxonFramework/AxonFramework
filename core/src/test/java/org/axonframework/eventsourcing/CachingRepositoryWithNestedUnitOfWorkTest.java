/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.eventsourcing;

import net.sf.ehcache.CacheManager;
import org.axonframework.cache.Cache;
import org.axonframework.cache.EhCacheAdapter;
import org.axonframework.cache.NoCache;
import org.axonframework.eventhandling.*;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.eventstore.fs.SimpleEventFileResolver;
import org.axonframework.eventstore.jpa.JpaEventStore;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * Minimal test cases triggering an issue with the NestedUnitOfWork and the CachingEventSourcingRepository, see <a
 * href="http://issues.axonframework.org/youtrack/issue/AXON-180">AXON-180</a>.
 * <p/>
 * The caching event sourcing repository loads aggregates from a cache first, from the event store second. It places
 * aggregates into the cache via a postCommit listener registered with the unit of work.
 * <p/>
 * Committing a unit of work may result in additinal units of work being created -- typically as part of the 'publish
 * event' phase:
 * <ol>
 * <li>A Command is dispatched.
 * <li>A UOW is created, started.
 * <li>The command completes. Aggregate(s) have been loaded and events have been applied.
 * <li>The UOW is comitted:
 * <ol>
 * <li>The aggregate is saved.
 * <li>Events are published on the event bus.
 * <li>Inner UOWs are comitted.
 * <li>AfterCommit listeners are notified.
 * </ol>
 * </ol>
 * <p/>
 * When the events are published, an @EventHandler may dispatch an additional command, which creates its own UOW
 * following above cycle.
 * <p/>
 * When UnitsOfWork are nested, it's possible to create a situation where one UnitOfWork completes the "innerCommit"
 * (and notifies afterCommit listeners) while subsequent units of work have yet to be created.
 * <p/>
 * That means that the CachingEventSourcingRepository's afterCommit listener will place the aggregate (from this UOW)
 * into the cache, exposing it to subsequent UOWs. The state of the aggregate in any UOW is not guaranteed to be
 * up-to-date. Depending on how UOWs are nested, it may be 'behind' by several events;
 * <p/>
 * Any subsequent UOW (after an aggregate was added to the cache) works on potentially stale data. This manifests
 * itself
 * primarily by events being assigned duplicate sequence numbers. The {@link JpaEventStore} detects this and throws an
 * exception noting that an 'identical' entity has already been persisted. The {@link FileSystemEventStore} corrupts
 * data silently.
 * <p/>
 * <p/>
 * <h2>Possible solutions and workarounds contemplated include:</h2>
 * <p/>
 * <ul>
 * <li>Workaround: Disable Caching. Aggregates are always loaded from the event store for each UOW.
 * <li>Defer 'afterCommit' listener notification until the parent UOW completes.
 * <li>Prevent nesting of UOWs more than one level -- Attach all new UOWs to the 'root' UOW and let it manage event
 * publication while watching for newly created UOWs.
 * <li>Place Aggregates in the cache immediately. Improves performance by avoiding multiple loads of an aggregate (once
 * per UOW) and ensures that UOWs work on the same, up-to-date instance of the aggregate. Not desirable with a
 * distributed cache w/o global locking.
 * <li>Maintain a 'Session' of aggregates used in a UOW -- similar to adding aggregates to the cache immediately, but
 * explicitly limiting the scope/visibility to a UOW (or group of UOWs). Similar idea to a JPA/Hibernate session:
 * provide quick and canonical access to aggregates already touched somewhere in this UOW.
 * </ul>
 *
 * @author patrickh
 */
//todo fix test
@Ignore
public class CachingRepositoryWithNestedUnitOfWorkTest {

    CachingEventSourcingRepository<Aggregate> repository;
    UnitOfWorkFactory uowFactory;
    EventBus eventBus;
    Cache cache;

    final List<String> events = new ArrayList<>();

    private SimpleEventProcessor eventProcessor;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        final CacheManager cacheManager = CacheManager.getInstance();
        cache = new EhCacheAdapter(cacheManager.addCacheIfAbsent("name"));

        eventBus = new SimpleEventBus();
        eventProcessor = new SimpleEventProcessor("logging");
        eventProcessor.subscribe(new LoggingEventListener(events));
        eventBus.subscribe(eventProcessor);
        events.clear();

        EventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(tempFolder.newFolder()));
        AggregateFactory<Aggregate> aggregateFactory = new GenericAggregateFactory<>(Aggregate.class);
        repository = new CachingEventSourcingRepository<>(aggregateFactory, eventStore);
        repository.setEventBus(eventBus);

        uowFactory = new DefaultUnitOfWorkFactory();
    }

    @Test
    public void testWithoutCache() {
        repository.setCache(NoCache.INSTANCE);
        executeComplexScenario("ComplexWithoutCache");
    }

    @Test
    public void testWithCache() {
        repository.setCache(cache);
        executeComplexScenario("ComplexWithCache");
    }

    @Test
    public void testMinimalScenarioWithoutCache() {
        repository.setCache(NoCache.INSTANCE);
        testMinimalScenario("MinimalScenarioWithoutCache");
    }

    @Test
    public void testMinimalScenarioWithCache() {
        repository.setCache(cache);
        testMinimalScenario("MinimalScenarioWithCache");
    }


    public void testMinimalScenario(String id) {
        // Execute commands to update this aggregate after the creation (previousToken = null)
        eventProcessor.subscribe(new CommandExecutingEventListener("1", null, true));
        eventProcessor.subscribe(new CommandExecutingEventListener("2", null, true));

        UnitOfWork<?> uow = uowFactory.createUnitOfWork(null);
        repository.add(new Aggregate(id));
        uow.commit();

        Aggregate verify = loadAggregate(id);
        assertEquals(2, verify.tokens.size());
        assertTrue(verify.tokens.containsAll(asList("1", "2")));
    }

    private void executeComplexScenario(String id) {
        // Unit Of Work hierarchy:
        // root ("2")
        //		4
        //			8
        // 				10 <- rolled back
        //			9
        //		5
        //		3
        //			6
        //				7
        //

        // Execute commands to update this aggregate after the creation (previousToken = null)
        eventProcessor.subscribe(new CommandExecutingEventListener("UOW4", null, true));
        eventProcessor.subscribe(new CommandExecutingEventListener("UOW5", null, true));
        eventProcessor.subscribe(new CommandExecutingEventListener("UOW3", null, true));

        // Execute commands to update after the previous update has been performed
        eventProcessor.subscribe(new CommandExecutingEventListener("UOW7", "UOW6", true));
        eventProcessor.subscribe(new CommandExecutingEventListener("UOW6", "UOW3", true));

        eventProcessor.subscribe(new CommandExecutingEventListener("UOW10", "UOW8", false)); // roll back
        eventProcessor.subscribe(new CommandExecutingEventListener("UOW9", "UOW4", true));
        eventProcessor.subscribe(new CommandExecutingEventListener("UOW8", "UOW4", true));

        Aggregate a = new Aggregate(id);

        // First command: Create Aggregate
        UnitOfWork<?> uow1 = uowFactory.createUnitOfWork(null);
        repository.add(a);
        uow1.commit();

        Aggregate verify = loadAggregate(id);

        assertEquals(id, verify.id);
        assertEquals(7, verify.tokens.size());
        assertTrue(verify.tokens.containsAll(asList(//
                                                    "UOW3", "UOW4", "UOW5", "UOW6", "UOW7", "UOW8", "UOW9")));
        assertFalse(verify.tokens.contains("UOW10"));
        for (int i = 0; i < verify.tokens.size(); i++) {
            assertTrue("Expected event with sequence number " + i + " but got :" + events.get(i),
                       events.get(i).startsWith(i + " "));
        }
    }

    private Aggregate loadAggregate(String id) {
        UnitOfWork<?> uow = uowFactory.createUnitOfWork(null);
        Aggregate verify = repository.load(id);
        uow.rollback();
        return verify;
    }

    /**
     * Capture information about events that are published
     */
    static final class LoggingEventListener implements EventListener {

        private final List<String> events;

        private LoggingEventListener(List<String> events) {
            this.events = events;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public void handle(EventMessage event) {
            GenericDomainEventMessage e = (GenericDomainEventMessage) event;
            String str = String.format("%d - %s(%s) ID %s %s", //
                                       e.getSequenceNumber(), //
                                       e.getPayloadType().getSimpleName(), //
                                       e.getAggregateIdentifier(), //
                                       e.getIdentifier(), //
                                       e.getPayload());
            events.add(str);
        }
    }

    /**
     * Simulate event on bus -> command handler -> subsequent command (w/ unit of work)
     */
    private final class CommandExecutingEventListener implements EventListener {

        final String token;
        final String previousToken;
        private final boolean commit;

        public CommandExecutingEventListener(String token, String previousToken, boolean commit) {
            this.token = token;
            this.previousToken = previousToken;
            this.commit = commit;
        }

        @Override
        public void handle(@SuppressWarnings("rawtypes") EventMessage event) {
            Object payload = event.getPayload();

            if (previousToken == null && payload instanceof AggregateCreatedEvent) {
                AggregateCreatedEvent created = (AggregateCreatedEvent) payload;

                UnitOfWork<EventMessage<?>> nested = uowFactory.createUnitOfWork(event);
                Aggregate aggregate = repository.load(created.id);
                aggregate.update(token);
                nested.commit();
            }

            if (previousToken != null && payload instanceof AggregateUpdatedEvent) {
                AggregateUpdatedEvent updated = (AggregateUpdatedEvent) payload;
                if (updated.token.equals(previousToken)) {
                    UnitOfWork<EventMessage<?>> nested = uowFactory.createUnitOfWork(event);
                    Aggregate aggregate = repository.load(updated.id);
                    aggregate.update(token);
                    if (commit) {
                        nested.commit();
                    } else {
                        nested.rollback();
                    }
                }
            }
        }
    }

	/*
     * Domain Model
	 */

    public static class AggregateCreatedEvent implements Serializable {

        @AggregateIdentifier
        final String id;

        public AggregateCreatedEvent(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public static class AggregateUpdatedEvent implements Serializable {

        @AggregateIdentifier
        final String id;
        final String token;

        public AggregateUpdatedEvent(String id, String token) {
            this.id = id;
            this.token = token;
        }

        @Override
        public String toString() {
            return id + "/" + token;
        }
    }

    @SuppressWarnings("serial")
    public static class Aggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        public String id;

        public Set<String> tokens = new HashSet<>();

        @SuppressWarnings("unused")
        private Aggregate() {
        }

        public Aggregate(String id) {
            apply(new AggregateCreatedEvent(id));
        }

        public void update(String token) {
            apply(new AggregateUpdatedEvent(id, token));
        }

        @EventSourcingHandler
        private void created(AggregateCreatedEvent event) {
            this.id = event.id;
        }

        @EventSourcingHandler
        private void updated(AggregateUpdatedEvent event) {
            tokens.add(event.token);
        }
    }
}
