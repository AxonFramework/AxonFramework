/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TagEventCriteria;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AsyncEventSourcingRepository}.
 */
class AsyncEventSourcingRepositoryTest {

    private static final String TEST_CONTEXT = "DEFAULT_CONTEXT";
    private static final TagEventCriteria TEST_MODEL_CRITERIA = EventCriteria.matchesTag(new Tag("aggregateId", "id"));

    private AsyncEventStore eventStore;
    private EventStoreTransaction eventStoreTransaction;

    private AsyncEventSourcingRepository<String, String> testSubject;

    @BeforeEach
    void setUp() {
        eventStore = mock();
        eventStoreTransaction = mock();
        when(eventStore.transaction(any(), eq(TEST_CONTEXT))).thenReturn(eventStoreTransaction);

        testSubject = new AsyncEventSourcingRepository<>(
                eventStore,
                identifier -> TEST_MODEL_CRITERIA,
                (currentState, event, ctx) -> currentState + "-" + event.getPayload(),
                id -> id,
                TEST_CONTEXT
        );
    }

    @Test
    void loadEventSourcedEntity() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally(), () -> result.exceptionNow().toString());
        verify(eventStore, times(2)).transaction(processingContext, TEST_CONTEXT);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

        assertEquals("test-0-1", result.resultNow().entity());
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
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

        ManagedEntity<String, String> result = testSubject.load("test", processingContext).get();

        testSubject.attach(result, processingContext2);

        verify(eventStoreTransaction, times(2)).onAppend(any());
    }

    @Test
    void assigningEntityToOtherProcessingContextInOtherFormat() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();
        ProcessingContext processingContext2 = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

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
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        verify(eventStore, times(2)).transaction(processingContext, TEST_CONTEXT);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));
        //noinspection unchecked
        ArgumentCaptor<Consumer<EventMessage<?>>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(eventStoreTransaction).onAppend(callback.capture());
        assertEquals("test-0-1", result.resultNow().entity());

        callback.getValue().accept(new GenericEventMessage<>(new MessageType("event"), "live"));
        assertEquals("test-0-1-live", result.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldLoadWhenEventsAreReturned() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> result =
                testSubject.loadOrCreate("test", processingContext);

        assertEquals("test-0-1", result.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldCreateWhenNoEventsAreReturned() {
        StubProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.empty())
                .when(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

        CompletableFuture<ManagedEntity<String, String>> loaded =
                testSubject.loadOrCreate("test", processingContext);

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        verify(eventStore, times(2)).transaction(processingContext, TEST_CONTEXT);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(AsyncEventSourcingRepositoryTest::conditionPredicate));

        assertEquals("test", loaded.resultNow().entity());
    }

    private static boolean conditionPredicate(SourcingCondition condition) {
        return condition.matches("ignored", Set.of(TEST_MODEL_CRITERIA.tag()));
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    private static DomainEventMessage<?> domainEvent(int seq) {
        return new GenericDomainEventMessage<>("test", "id", seq, new MessageType("event"), seq);
    }
}