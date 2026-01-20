/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.command;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Version;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.DomainEventSequenceAware;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.internal.stubbing.answers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link GenericJpaRepository}.
 *
 * @author Allard Buijze
 */
class GenericJpaRepositoryTest {

    private EntityManager mockEntityManager;
    private GenericJpaRepository<StubJpaAggregate> testSubject;
    private String aggregateId;
    private StubJpaAggregate aggregate;
    private Function<String, ?> identifierConverter;
    private EventBus eventBus;
    private TestSpanFactory spanFactory;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        mockEntityManager = mock(EntityManager.class);
        //noinspection unchecked
        identifierConverter = mock(Function.class);
        eventBus = new SimpleEventBus();
        when(identifierConverter.apply(anyString())).thenAnswer(i -> i.getArguments()[0]);
        testSubject =
                GenericJpaRepository.builder(StubJpaAggregate.class)
                                          .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                          .eventBus(eventBus)
                                          .identifierConverter(identifierConverter)
                                          .spanFactory(DefaultRepositorySpanFactory.builder()
                                                                                   .spanFactory(spanFactory)
                                                                                   .build())
                                          .build();
        LegacyDefaultUnitOfWork.startAndGet(null);
        aggregateId = "123";
        aggregate = new StubJpaAggregate(aggregateId);
        when(mockEntityManager.find(eq(StubJpaAggregate.class), eq("123"), any(LockModeType.class)))
                .thenReturn(aggregate);
    }

    @AfterEach
    void cleanUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    @Disabled("TODO #3462")
    void aggregateStoredBeforeEventsPublished() throws Exception {
        OrderTrackingListener listener = new OrderTrackingListener();
        eventBus.subscribe(listener);

        testSubject.newInstance(() -> new StubJpaAggregate("test", "payload1", "payload2"));
        CurrentUnitOfWork.commit();

        InOrder inOrder = inOrder(mockEntityManager);
        inOrder.verify(mockEntityManager).persist(any());
        assertTrue(listener.wasInvoked(), "Listener should have been invoked");
    }

    @Test
    void loadAggregate() {
        Aggregate<StubJpaAggregate> actualResult = testSubject.load(aggregateId);
        assertSame(aggregate, actualResult.invoke(Function.identity()));
    }

    @Test
    void loadAggregateTracing() {
        when(mockEntityManager.find(eq(StubJpaAggregate.class), eq("123"), any(LockModeType.class)))
                .thenAnswer(invocation -> {
                    spanFactory.verifySpanCompleted("Repository.obtainLock");
                    spanFactory.verifySpanActive("Repository.load");
                    return aggregate;
                });
        testSubject.load(aggregateId);
        spanFactory.verifySpanCompleted("Repository.load");
        spanFactory.verifySpanHasAttributeValue("Repository.load", "axon.aggregateId", aggregateId);
    }

    @Test
    void loadAggregateWithConverter() {
        when(identifierConverter.apply("original")).thenAnswer(new Returns(aggregateId));
        Aggregate<StubJpaAggregate> actualResult = testSubject.load("original");
        assertSame(aggregate, actualResult.invoke(Function.identity()));
    }

    @Test
    @Disabled("TODO #3462")
    void aggregateCreatesSequenceNumbersForNewAggregatesWhenUsingDomainEventSequenceAwareEventBus() {
        DomainSequenceAwareEventBus testEventBus = new DomainSequenceAwareEventBus();

        testSubject = GenericJpaRepository.builder(StubJpaAggregate.class)
                                                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                                .eventBus(testEventBus)
                                                .identifierConverter(identifierConverter)
                                                .build();

        LegacyDefaultUnitOfWork.startAndGet(null).executeWithResult((ctx) -> {
            Aggregate<StubJpaAggregate> agg =
                    testSubject.newInstance(() -> new StubJpaAggregate("id", "test1", "test2"));
            agg.execute(e -> e.doSomething("test3"));
            return null;
        });
        CurrentUnitOfWork.commit();

        List<EventMessage> publishedEvents = testEventBus.getPublishedEvents();
        assertEquals(3, publishedEvents.size());

        EventMessage eventOne = publishedEvents.get(0);
        assertTrue(eventOne instanceof DomainEventMessage);
        DomainEventMessage domainEventOne = (DomainEventMessage) eventOne;
        assertEquals("test1", domainEventOne.payload());
        assertEquals(0, domainEventOne.getSequenceNumber());
        assertEquals("id", domainEventOne.getAggregateIdentifier());

        EventMessage eventTwo = publishedEvents.get(1);
        assertTrue(eventTwo instanceof DomainEventMessage);
        DomainEventMessage domainEventTwo = (DomainEventMessage) eventTwo;
        assertEquals("test2", domainEventTwo.payload());
        assertEquals(1, domainEventTwo.getSequenceNumber());
        assertEquals("id", domainEventTwo.getAggregateIdentifier());

        EventMessage eventThree = publishedEvents.get(2);
        assertTrue(eventThree instanceof DomainEventMessage);
        DomainEventMessage domainEventThree = (DomainEventMessage) eventThree;
        assertEquals("test3", domainEventThree.payload());
        assertEquals(2, domainEventThree.getSequenceNumber());
        assertEquals("id", domainEventThree.getAggregateIdentifier());
    }

    @Test
    void aggregateDoesNotCreateSequenceNumbersWhenEventBusIsNotDomainEventSequenceAware() {
        SimpleEventBus testEventBus = new SimpleEventBus();
        OrderTrackingListener listener = new OrderTrackingListener();
        testEventBus.subscribe(listener);

        testSubject = GenericJpaRepository.builder(StubJpaAggregate.class)
                                                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                                .eventBus(testEventBus)
                                                .identifierConverter(identifierConverter)
                                                .build();

        LegacyDefaultUnitOfWork.startAndGet(null).executeWithResult((ctx) -> {
            Aggregate<StubJpaAggregate> agg = testSubject.load(aggregateId);
            agg.execute(e -> e.doSomething("test2"));
            return null;
        });

        CurrentUnitOfWork.commit();

        List<EventMessage> capturedEvents = listener.getReceivedEvents();

        assertEquals(1, capturedEvents.size());
        EventMessage eventOne = capturedEvents.get(0);
        assertFalse(eventOne instanceof DomainEventMessage);
        assertEquals("test2", eventOne.payload());
    }

    @Test
    @Disabled("TODO #3462")
    void aggregateDoesNotCreateSequenceNumbersWhenSequenceNumberGenerationIsDisabled() {
        String expectedFirstPayload = "test1";
        String expectedSecondPayload = "test2";
        String expectedThirdPayload = "test3";

        DomainSequenceAwareEventBus testEventBus = new DomainSequenceAwareEventBus();
        testSubject = GenericJpaRepository.builder(StubJpaAggregate.class)
                                                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                                .eventBus(testEventBus)
                                                .identifierConverter(identifierConverter)
                                                .disableSequenceNumberGeneration()
                                                .build();

        LegacyDefaultUnitOfWork.startAndGet(null)
                               .executeWithResult((ctx) -> {
                                   Aggregate<StubJpaAggregate> aggregate1 = testSubject.newInstance(
                                           () -> new StubJpaAggregate("id", expectedFirstPayload, expectedSecondPayload)
                                   );
                                   aggregate1.execute(e -> e.doSomething(expectedThirdPayload));
                                   return null;
                               });
        CurrentUnitOfWork.commit();

        List<EventMessage> publishedEvents = testEventBus.getPublishedEvents();
        assertEquals(3, publishedEvents.size());

        EventMessage eventOne = publishedEvents.get(0);
        assertFalse(eventOne instanceof DomainEventMessage);
        assertEquals(expectedFirstPayload, eventOne.payload());

        EventMessage eventTwo = publishedEvents.get(1);
        assertFalse(eventTwo instanceof DomainEventMessage);
        assertEquals(expectedSecondPayload, eventTwo.payload());

        EventMessage eventThree = publishedEvents.get(2);
        assertFalse(eventThree instanceof DomainEventMessage);
        assertEquals(expectedThirdPayload, eventThree.payload());
    }

    @Test
    void loadAggregate_NotFound() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        try {
            testSubject.load(aggregateIdentifier);
            fail("Expected AggregateNotFoundException");
        } catch (AggregateNotFoundException e) {
            assertEquals(aggregateIdentifier, e.getAggregateIdentifier());
        }
    }

    @Test
    void persistAggregate_DefaultFlushMode() {
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    void persistAggregate_ExplicitFlushModeOn() {
        testSubject.setForceFlushOnSave(true);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    void persistAggregate_ExplicitFlushModeOff() {
        testSubject.setForceFlushOnSave(false);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager, never()).flush();
    }

    @Test
    void buildWithNullSubtypesThrowsAxonConfigurationException() {
        GenericJpaRepository.Builder<StubJpaAggregate> builderTestSubject =
                GenericJpaRepository.builder(StubJpaAggregate.class)
                                          .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                          .eventBus(eventBus);

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.subtypes(null));
    }

    @Test
    void buildWithNullSubtypeThrowsAxonConfigurationException() {
        GenericJpaRepository.Builder<StubJpaAggregate> builderTestSubject =
                GenericJpaRepository.builder(StubJpaAggregate.class)
                                          .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                          .eventBus(eventBus);

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.subtype(null));
    }

    private class StubJpaAggregate {

        @Id
        private final String identifier;

        @SuppressWarnings("unused")
        @AggregateVersion
        @Version
        private long version;

        private StubJpaAggregate(String identifier) {
            this.identifier = identifier;
        }

        private StubJpaAggregate(String identifier, String... payloads) {
            this(identifier);
            for (String payload : payloads) {
                apply(payload);
            }
        }

        public void doSomething(String newValue) {
            apply(newValue);
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    private static class DomainSequenceAwareEventBus extends SimpleEventBus implements DomainEventSequenceAware {

        private final List<EventMessage> publishedEvents = new ArrayList<>();
        private final Map<String, Long> sequencePerAggregate = new HashMap<>();

        DomainSequenceAwareEventBus() {
        }

        @Override
        public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                               @Nonnull List<EventMessage> events) {
            publishedEvents.addAll(events);
            return super.publish(context, events);
        }

        List<EventMessage> getPublishedEvents() {
            return publishedEvents;
        }

        @Override
        public Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
            if (!sequencePerAggregate.containsKey(aggregateIdentifier)) {
                sequencePerAggregate.put(aggregateIdentifier, 0L);
            }
            return Optional.ofNullable(
                    sequencePerAggregate.computeIfPresent(aggregateIdentifier, (aggregateId, seqNo) -> seqNo++)
            );
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {

        }
    }

    /**
     * Listener that tracks invocation order relative to other operations.
     */
    private static class OrderTrackingListener implements BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> {
        private final List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
        private volatile boolean invoked = false;

        @Override
        public CompletableFuture<?> apply(List<? extends EventMessage> events, ProcessingContext context) {
            invoked = true;
            receivedEvents.addAll(events);
            return CompletableFuture.completedFuture(null);
        }

        public boolean wasInvoked() {
            return invoked;
        }

        public List<EventMessage> getReceivedEvents() {
            return new ArrayList<>(receivedEvents);
        }
    }
}
