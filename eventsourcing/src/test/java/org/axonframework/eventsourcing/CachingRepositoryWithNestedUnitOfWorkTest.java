/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.EhCacheAdapter;
import org.axonframework.common.caching.NoCache;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.Ehcache;
import org.ehcache.core.EhcacheManager;
import org.ehcache.core.config.DefaultConfiguration;
import org.junit.jupiter.api.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal test cases triggering an issue with the NestedUnitOfWork and the CachingEventSourcingRepository, see <a
 * href="http://issues.axonframework.org/youtrack/issue/AXON-180">AXON-180</a>.
 * <p/>
 * The caching event sourcing repository loads aggregates from a cache first, from the event store second. It places
 * aggregates into the cache via a postCommit listener registered with the unit of work.
 * <p/>
 * Committing a unit of work may result in additional units of work being created -- typically as part of the 'publish
 * event' phase: <ol> <li>A Command is dispatched. <li>A UOW is created, started. <li>The command completes.
 * Aggregate(s) have been loaded and events have been applied. <li>The UOW is committed: <ol> <li>The aggregate is
 * saved.
 * <li>Events are published on the event bus. <li>Inner UOWs are committed. <li>AfterCommit listeners are notified.
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
 * primarily by events being assigned duplicate sequence numbers. The {@link org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine}
 * detects this and throws an
 * exception noting that an 'identical' entity has already been persisted.
 * <p/>
 * <p/>
 * <h2>Possible solutions and workarounds contemplated include:</h2>
 * <p/>
 * <ul> <li>Workaround: Disable Caching. Aggregates are always loaded from the event store for each UOW. <li>Defer
 * 'afterCommit' listener notification until the parent UOW completes. <li>Prevent nesting of UOWs more than one level
 * -- Attach all new UOWs to the 'root' UOW and let it manage event publication while watching for newly created UOWs.
 * <li>Place Aggregates in the cache immediately. Improves performance by avoiding multiple loads of an aggregate (once
 * per UOW) and ensures that UOWs work on the same, up-to-date instance of the aggregate. Not desirable with a
 * distributed cache w/o global locking. <li>Maintain a 'Session' of aggregates used in a UOW -- similar to adding
 * aggregates to the cache immediately, but explicitly limiting the scope/visibility to a UOW (or group of UOWs).
 * Similar idea to a JPA/Hibernate session: provide quick and canonical access to aggregates already touched somewhere
 * in this UOW. </ul>
 *
 * @author patrickh
 */
class CachingRepositoryWithNestedUnitOfWorkTest {

    private final List<String> events = new ArrayList<>();
    private CachingEventSourcingRepository<TestAggregate> repository;
    private Cache realCache;
    private AggregateFactory<TestAggregate> aggregateFactory;
    private EventStore eventStore;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        Map<String, CacheConfiguration<?, ?>> caches = new HashMap<>();
        DefaultConfiguration config = new DefaultConfiguration(caches, null);
        cacheManager = new EhcacheManager(config);
        cacheManager.init();
        realCache = new EhCacheAdapter((Ehcache) cacheManager.createCache(
                "name",
                CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(
                                Object.class,
                                Object.class,
                                ResourcePoolsBuilder
                                        .newResourcePoolsBuilder()
                                        .heap(1, MemoryUnit.MB)
                                        .build())
                        .build()));


        eventStore = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        SimpleEventHandlerInvoker eventHandlerInvoker =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(new LoggingEventHandler(events))
                                         .build();
        SubscribingEventProcessor eventProcessor =
                SubscribingEventProcessor.builder()
                                         .name("test")
                                         .eventHandlerInvoker(eventHandlerInvoker)
                                         .messageSource(eventStore)
                                         .build();
        eventProcessor.start();
        events.clear();
        aggregateFactory = new GenericAggregateFactory<>(TestAggregate.class);
    }

    @AfterEach
    void tearDown() {
        cacheManager.close();
    }


    @Test
    void withoutCache() throws Exception {
        repository = CachingEventSourcingRepository.builder(TestAggregate.class)
                                                   .aggregateFactory(aggregateFactory)
                                                   .eventStore(eventStore)
                                                   .cache(NoCache.INSTANCE)
                                                   .build();
        executeComplexScenario("ComplexWithoutCache");
    }

    @Test
    void withCache() throws Exception {
        repository = CachingEventSourcingRepository.builder(TestAggregate.class)
                .aggregateFactory(aggregateFactory)
                .eventStore(eventStore)
                .cache(realCache)
                .build();
        executeComplexScenario("ComplexWithCache");
    }

    @Test
    void minimalScenarioWithoutCache() throws Exception {
        repository = CachingEventSourcingRepository.builder(TestAggregate.class)
                .aggregateFactory(aggregateFactory)
                .eventStore(eventStore)
                .cache(NoCache.INSTANCE)
                .build();
        testMinimalScenario("MinimalScenarioWithoutCache");
    }

    @Test
    void minimalScenarioWithCache() throws Exception {
        repository = CachingEventSourcingRepository.builder(TestAggregate.class)
                .aggregateFactory(aggregateFactory)
                .eventStore(eventStore)
                .cache(realCache)
                .build();
        testMinimalScenario("MinimalScenarioWithCache");
    }


    private void testMinimalScenario(String id) throws Exception {
        // Execute commands to update this aggregate after the creation (previousToken = null)
        SimpleEventHandlerInvoker eventHandlerInvoker =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(
                                                 new CommandExecutingEventHandler("1", null, true),
                                                 new CommandExecutingEventHandler("2", null, true)
                                         )
                                         .build();
        SubscribingEventProcessor eventProcessor =
                SubscribingEventProcessor.builder()
                                         .name("test")
                                         .eventHandlerInvoker(eventHandlerInvoker)
                                         .messageSource(eventStore)
                                         .build();
        eventProcessor.start();

        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(null);
        repository.newInstance(() -> new TestAggregate(id));
        uow.commit();

        TestAggregate verify = loadAggregate(id);
        assertEquals(2, verify.tokens.size());
        assertTrue(verify.tokens.containsAll(asList("1", "2")));
    }

    private void executeComplexScenario(String id) throws Exception {
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
        SimpleEventHandlerInvoker eventHandlerInvoker =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(
                                                 new CommandExecutingEventHandler("UOW4", null, true),
                                                 new CommandExecutingEventHandler("UOW5", null, true),
                                                 new CommandExecutingEventHandler("UOW3", null, true),
                                                 // Execute commands to update after the previous update has been performed
                                                 new CommandExecutingEventHandler("UOW7", "UOW6", true),
                                                 new CommandExecutingEventHandler("UOW6", "UOW3", true),

                                                 new CommandExecutingEventHandler("UOW10", "UOW8", false),
                                                 new CommandExecutingEventHandler("UOW9", "UOW4", true),
                                                 new CommandExecutingEventHandler("UOW8", "UOW4", true)
                                         ).build();
        SubscribingEventProcessor eventProcessor =
                SubscribingEventProcessor.builder()
                                         .name("test")
                                         .eventHandlerInvoker(eventHandlerInvoker)
                                         .messageSource(eventStore)
                                         .build();
        eventProcessor.start();

        // First command: Create Aggregate
        UnitOfWork<?> uow1 = DefaultUnitOfWork.startAndGet(null);
        repository.newInstance(() -> new TestAggregate(id));
        uow1.commit();

        TestAggregate verify = loadAggregate(id);

        assertEquals(id, verify.id);
        assertTrue(verify.tokens.containsAll(asList(//
                                                    "UOW3", "UOW4", "UOW5", "UOW6", "UOW7", "UOW8", "UOW9")));
        assertFalse(verify.tokens.contains("UOW10"));
        assertEquals(7, verify.tokens.size());
        for (int i = 0; i < verify.tokens.size(); i++) {
            assertTrue(events.get(i).startsWith(i + " "), "Expected event with sequence number " + i + " but got :" + events.get(i));
        }
    }

    private TestAggregate loadAggregate(String id) {
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(null);
        Aggregate<TestAggregate> verify = repository.load(id);
        uow.rollback();
        return verify.invoke(Function.identity());
    }

    /**
     * Capture information about events that are published
     */
    private static final class LoggingEventHandler implements EventMessageHandler {

        private final List<String> events;

        private LoggingEventHandler(List<String> events) {
            this.events = events;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Object handle(EventMessage event) {
            GenericDomainEventMessage e = (GenericDomainEventMessage) event;
            String str = String.format("%d - %s(%s) ID %s %s", //
                                       e.getSequenceNumber(), //
                                       e.getPayloadType().getSimpleName(), //
                                       e.getAggregateIdentifier(), //
                                       e.getIdentifier(), //
                                       e.getPayload());
            events.add(str);
            return null;
        }
    }

    /*
     * Domain Model
     */

    public static class AggregateCreatedEvent implements Serializable {

        @AggregateIdentifier
        private final String id;

        public AggregateCreatedEvent(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + ": " + id;
        }
    }

    public static class AggregateUpdatedEvent implements Serializable {

        @AggregateIdentifier
        private final String id;
        private final String token;

        public AggregateUpdatedEvent(String id, String token) {
            this.id = id;
            this.token = token;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + ": " + id + "/" + token;
        }
    }

    public static class TestAggregate implements Serializable {

        @AggregateIdentifier
        public String id;

        public Set<String> tokens = new HashSet<>();

        @SuppressWarnings("unused")
        private TestAggregate() {
        }

        public TestAggregate(String id) {
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

    /**
     * Simulate event on bus -> command handler -> subsequent command (w/ unit of work)
     */
    private final class CommandExecutingEventHandler implements EventMessageHandler {

        private final String token;
        private final String previousToken;
        private final boolean commit;

        private CommandExecutingEventHandler(String token, String previousToken, boolean commit) {
            this.token = token;
            this.previousToken = previousToken;
            this.commit = commit;
        }

        @Override
        public Object handle(EventMessage<?> event) {
            Object payload = event.getPayload();

            if (previousToken == null && payload instanceof AggregateCreatedEvent) {
                AggregateCreatedEvent created = (AggregateCreatedEvent) payload;

                UnitOfWork<EventMessage<?>> nested = DefaultUnitOfWork.startAndGet(event);
                nested.execute(() -> {
                    Aggregate<TestAggregate> aggregate = repository.load(created.id);
                    aggregate.execute(r -> r.update(token));
                });
            }

            if (previousToken != null && payload instanceof AggregateUpdatedEvent) {
                AggregateUpdatedEvent updated = (AggregateUpdatedEvent) payload;
                if (updated.token.equals(previousToken)) {
                    UnitOfWork<EventMessage<?>> nested = DefaultUnitOfWork.startAndGet(event);
                    if (commit) {
                        nested.execute(() -> {
                            Aggregate<TestAggregate> aggregate = repository.load(updated.id);
                            aggregate.execute(r -> r.update(token));
                        });
                    } else {
                        try {
                            Aggregate<TestAggregate> aggregate = repository.load(updated.id);
                            aggregate.execute(r -> r.update(token));
                        } finally {
                            nested.rollback();
                        }
                    }
                }
            }
            return null;
        }
    }
}
