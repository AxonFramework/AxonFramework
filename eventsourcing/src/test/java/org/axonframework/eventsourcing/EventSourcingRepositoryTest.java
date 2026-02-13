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

package org.axonframework.eventsourcing;

import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.axonframework.messaging.eventhandling.EventTestUtils.createEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventSourcingRepository}.
 *
 * @author Allard Buijze
 */
class EventSourcingRepositoryTest {

    private static final Set<Tag> TEST_TAGS = Set.of(new Tag("aggregateId", "id"));
    private static final EventCriteria TEST_CRITERIA = EventCriteria.havingTags("aggregateId", "id");

    private EventStore eventStore;
    private EventStoreTransaction eventStoreTransaction;
    private EventSourcedEntityFactory<String, String> factory;

    private EventSourcingRepository<String, String> testSubject;

    @BeforeEach
    void setUp() {
        eventStore = mock();
        eventStoreTransaction = mock();
        when(eventStore.transaction(any(ProcessingContext.class))).thenReturn(eventStoreTransaction);
        when(eventStore.transaction(any(EventCriteria.class), any(ProcessingContext.class))).thenReturn(eventStoreTransaction);

        factory = (id, event, ctx) -> {
            if (event != null) {
                return id + "(" + event.payload() + ")";
            }
            return id + "()";
        };
        testSubject = new EventSourcingRepository<>(
                String.class,
                String.class,
                eventStore,
                (id, event, context) -> factory.create(id, event, context),
                (identifier, ctx) -> TEST_CRITERIA,
                (entity, event, context) -> entity + "-" + event.payload()
        );
    }

    @Test
    void loadEventSourcedEntity() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally(), () -> result.exceptionNow().toString());
        // Both doLoad and updateActiveEntity call transaction(EventCriteria, ProcessingContext)
        verify(eventStore, times(2)).transaction(any(EventCriteria.class), eq(processingContext));
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        assertEquals("test(0)-0-1", result.resultNow().entity());
    }

    @Test
    void persistNewEntityRegistersItToListenToEvents() {
        ProcessingContext processingContext = new StubProcessingContext();

        ManagedEntity<String, String> result = testSubject.persist("id", "entity", processingContext);

        verify(eventStoreTransaction).onAppend(any());
        assertEquals("entity", result.entity());
        assertEquals("id", result.identifier());
    }

    @Test
    void persistAlreadyPersistedEntityDoesNotRegisterItToListenToEvents() {
        ProcessingContext processingContext = new StubProcessingContext();

        ManagedEntity<String, String> first = testSubject.persist("id", "entity", processingContext);
        ManagedEntity<String, String> second = testSubject.persist("id", "entity", processingContext);

        verify(eventStoreTransaction).onAppend(any());
        assertSame(first, second);
        assertEquals("entity", first.entity());
        assertEquals("id", first.identifier());
    }

    @Test
    void assigningEntityToOtherProcessingContextInExactFormat() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();
        ProcessingContext processingContext2 = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        ManagedEntity<String, String> result = testSubject.load("test", processingContext).get();

        testSubject.attach(result, processingContext2);

        verify(eventStoreTransaction, times(2)).onAppend(any());
    }

    @Test
    void assigningEntityToOtherProcessingContextInOtherFormat() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();
        ProcessingContext processingContext2 = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        ManagedEntity<String, String> result = testSubject.load("test", processingContext).get();

        testSubject.attach(new ManagedEntity<>() {
            @Override
            public String identifier() {
                return result.identifier();
            }

            @Override
            public String entity() {
                return result.entity();
            }

            @Override
            public String applyStateChange(UnaryOperator<String> change) {
                fail("This should not have been invoked");
                return "ERROR";
            }
        }, processingContext2);

        verify(eventStoreTransaction, times(2)).onAppend(any());
    }

    @Test
    void updateLoadedEventSourcedEntity() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        // Both doLoad and updateActiveEntity call transaction(EventCriteria, ProcessingContext)
        verify(eventStore, times(2)).transaction(any(EventCriteria.class), eq(processingContext));
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));
        //noinspection unchecked
        ArgumentCaptor<Consumer<EventMessage>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(eventStoreTransaction).onAppend(callback.capture());
        assertEquals("test(0)-0-1", result.resultNow().entity());

        callback.getValue().accept(new GenericEventMessage(new MessageType("event"), "live"));
        assertEquals("test(0)-0-1-live", result.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldLoadWhenEventsAreReturned() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result =
                testSubject.load("test", processingContext);

        assertEquals("test(0)-0-1", result.resultNow().entity());
    }

    @Test
    void loadOrCreateThrowsExceptionWhenEventStreamIsEmptyAndNullEntityIsCreated() {
        ProcessingContext processingContext = new StubProcessingContext();
        factory = (id, event, ctx) -> {
            if (event != null) {
                return id + "(" + event.payload() + ")";
            }
            return null; // Simulating a null entity creation
        };
        doReturn(MessageStream.fromStream(Stream.of()))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.loadOrCreate("test", processingContext);
        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(EntityMissingAfterLoadOrCreateException.class, result.exceptionNow());
    }

    @Test
    void loadThrowsExceptionIfNullEntityIsReturnedAfterFirstEvent() {
        ProcessingContext processingContext = new StubProcessingContext();
        factory = (id, event, ctx) -> {
            return null; // Simulating a null entity creation
        };
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(EntityMissingAfterFirstEventException.class, result.exceptionNow());
    }

    @Test
    void loadShouldReturnNullEntityWhenNoEventsAreReturned() {
        StubProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.empty())
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> loaded =
                testSubject.load("test", processingContext);

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        // Both doLoad and updateActiveEntity call transaction(EventCriteria, ProcessingContext)
        verify(eventStore, times(2)).transaction(any(EventCriteria.class), eq(processingContext));
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        assertNull(loaded.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldReturnNoEventMessageConstructorEntityWhenNoEventsAreReturned() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.empty())
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> loaded =
                testSubject.loadOrCreate("test", processingContext);

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        // Both doLoad and updateActiveEntity call transaction(EventCriteria, ProcessingContext)
        verify(eventStore, times(2)).transaction(any(EventCriteria.class), eq(processingContext));
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate));

        assertEquals("test()", loaded.resultNow().entity());
    }

    private static boolean conditionPredicate(SourcingCondition condition) {
        return condition.matches(new QualifiedName("ignored"), TEST_TAGS);
    }
}