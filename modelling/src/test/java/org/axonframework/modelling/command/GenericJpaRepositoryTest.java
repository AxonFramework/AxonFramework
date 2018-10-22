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

package org.axonframework.modelling.command;

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.DomainEventSequenceAware;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.*;
import org.mockito.*;
import org.mockito.internal.stubbing.answers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.LockModeType;
import javax.persistence.Version;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class GenericJpaRepositoryTest {

    private EntityManager mockEntityManager;
    private GenericJpaRepository<StubJpaAggregate> testSubject;
    private String aggregateId;
    private StubJpaAggregate aggregate;
    private Function<String, ?> identifierConverter;
    private EventBus eventBus;

    @Before
    public void setUp() {
        mockEntityManager = mock(EntityManager.class);
        //noinspection unchecked
        identifierConverter = mock(Function.class);
        eventBus = SimpleEventBus.builder().build();
        when(identifierConverter.apply(anyString())).thenAnswer(i -> i.getArguments()[0]);
        testSubject = GenericJpaRepository.builder(StubJpaAggregate.class)
                                          .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                          .eventBus(eventBus)
                                          .identifierConverter(identifierConverter)
                                          .build();
        DefaultUnitOfWork.startAndGet(null);
        aggregateId = "123";
        aggregate = new StubJpaAggregate(aggregateId);
        when(mockEntityManager.find(eq(StubJpaAggregate.class), eq("123"), any(LockModeType.class)))
                .thenReturn(aggregate);
    }

    @After
    public void cleanUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testAggregateStoredBeforeEventsPublished() throws Exception {
        //noinspection unchecked
        Consumer<List<? extends EventMessage<?>>> mockConsumer = mock(Consumer.class);
        eventBus.subscribe(mockConsumer);

        testSubject.newInstance(() -> new StubJpaAggregate("test", "payload1", "payload2"));
        CurrentUnitOfWork.commit();

        InOrder inOrder = inOrder(mockEntityManager, mockConsumer);
        inOrder.verify(mockEntityManager).persist(any());
        inOrder.verify(mockConsumer).accept(anyList());
    }

    @Test
    public void testLoadAggregate() {
        Aggregate<StubJpaAggregate> actualResult = testSubject.load(aggregateId);
        assertSame(aggregate, actualResult.invoke(Function.identity()));
    }

    @Test
    public void testLoadAggregateWithConverter() {
        when(identifierConverter.apply("original")).thenAnswer(new Returns(aggregateId));
        Aggregate<StubJpaAggregate> actualResult = testSubject.load("original");
        assertSame(aggregate, actualResult.invoke(Function.identity()));
    }

    @Test
    public void testAggregateCreatesSequenceNumbersForNewAggregatesWhenUsingDomainEventSequenceAwareEventBus() {
        DomainSequenceAwareEventBus testEventBus = new DomainSequenceAwareEventBus();

        testSubject = GenericJpaRepository.builder(StubJpaAggregate.class)
                                          .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                          .eventBus(testEventBus)
                                          .identifierConverter(identifierConverter)
                                          .build();

        DefaultUnitOfWork.startAndGet(null).executeWithResult(() -> {
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
        assertEquals("test1", domainEventOne.getPayload());
        assertEquals(0, domainEventOne.getSequenceNumber());
        assertEquals("id", domainEventOne.getAggregateIdentifier());

        EventMessage eventTwo = publishedEvents.get(1);
        assertTrue(eventTwo instanceof DomainEventMessage);
        DomainEventMessage domainEventTwo = (DomainEventMessage) eventTwo;
        assertEquals("test2", domainEventTwo.getPayload());
        assertEquals(1, domainEventTwo.getSequenceNumber());
        assertEquals("id", domainEventTwo.getAggregateIdentifier());

        EventMessage eventThree = publishedEvents.get(2);
        assertTrue(eventThree instanceof DomainEventMessage);
        DomainEventMessage domainEventThree = (DomainEventMessage) eventThree;
        assertEquals("test3", domainEventThree.getPayload());
        assertEquals(2, domainEventThree.getSequenceNumber());
        assertEquals("id", domainEventThree.getAggregateIdentifier());
    }

    @Test
    public void testAggregateDoesNotCreateSequenceNumbersWhenEventBusIsNotDomainEventSequenceAware() {
        SimpleEventBus testEventBus = spy(SimpleEventBus.builder().build());

        testSubject = GenericJpaRepository.builder(StubJpaAggregate.class)
                                          .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                                          .eventBus(testEventBus)
                                          .identifierConverter(identifierConverter)
                                          .build();

        DefaultUnitOfWork.startAndGet(null).executeWithResult(() -> {
            Aggregate<StubJpaAggregate> agg = testSubject.load(aggregateId);
            agg.execute(e -> e.doSomething("test2"));
            return null;
        });

        CurrentUnitOfWork.commit();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<? extends EventMessage<?>>> eventCaptor =
                ArgumentCaptor.forClass((Class<List<? extends EventMessage<?>>>) (Class) List.class);

        verify(testEventBus).publish(eventCaptor.capture());
        List<? extends EventMessage<?>> capturedEvents = eventCaptor.getValue();

        assertEquals(1, capturedEvents.size());
        EventMessage<?> eventOne = capturedEvents.get(0);
        assertFalse(eventOne instanceof DomainEventMessage);
        assertEquals("test2", eventOne.getPayload());
    }

    @Test
    public void testLoadAggregate_NotFound() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        try {
            testSubject.load(aggregateIdentifier);
            fail("Expected AggregateNotFoundException");
        } catch (AggregateNotFoundException e) {
            assertEquals(aggregateIdentifier, e.getAggregateIdentifier());
        }
    }

    @Test
    public void testLoadAggregate_WrongVersion() {
        try {
            testSubject.load(aggregateId, 2L);
            fail("Expected ConflictingAggregateVersionException");
        } catch (ConflictingAggregateVersionException e) {
            assertEquals(2L, e.getExpectedVersion());
            assertEquals(0L, e.getActualVersion());
        }
    }

    @Test
    public void testPersistAggregate_DefaultFlushMode() {
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    public void testPersistAggregate_ExplicitFlushModeOn() {
        testSubject.setForceFlushOnSave(true);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    public void testPersistAggregate_ExplicitFlushModeOff() {
        testSubject.setForceFlushOnSave(false);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager, never()).flush();
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

    private class DomainSequenceAwareEventBus extends SimpleEventBus implements DomainEventSequenceAware {

        private List<EventMessage> publishedEvents = new ArrayList<>();
        private Map<String, Long> sequencePerAggregate = new HashMap<>();

        protected DomainSequenceAwareEventBus() {
            super(SimpleEventBus.builder());
        }

        @Override
        public void publish(List<? extends EventMessage<?>> events) {
            publishedEvents.addAll(events);
            super.publish(events);
        }

        public List<EventMessage> getPublishedEvents() {
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
    }
}
