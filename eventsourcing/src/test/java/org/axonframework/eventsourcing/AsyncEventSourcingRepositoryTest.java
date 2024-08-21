/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.FutureUtils;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.mockito.*;

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

    private AsyncEventStore eventStore;
    private EventStoreTransaction eventTransaction;

    private AsyncEventSourcingRepository<String, String> testSubject;

    @BeforeEach
    void setUp() {
        eventStore = mock();
        eventTransaction = mock();
        when(eventStore.transaction(any(), eq(TEST_CONTEXT))).thenReturn(eventTransaction);

        testSubject = new AsyncEventSourcingRepository<>(
                eventStore,
                identifier -> "id",
                (currentState, event) -> currentState + "-" + event.getPayload(),
                TEST_CONTEXT
        );
    }

    @Test
    void loadEventSourcedEntity() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStore)
                .source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types().contains("id")), );

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally(), () -> FutureUtils.unwrapMessage(result.exceptionNow()));
        verify(eventTransaction).onAppend(any());
        verify(eventStore).source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types().contains("id")), );

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally(), () -> FutureUtils.unwrapMessage(result.exceptionNow()));
        verify(eventTransaction).onAppend(any());
        verify(eventStore).source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types().contains("id")), );

        assertEquals("-0-1", result.resultNow().entity());
    }

    @Test
    void persistNewEntityRegistersItToListenToEvents() {
        ProcessingContext processingContext = new StubProcessingContext();

        ManagedEntity<String, String> result = testSubject.persist("id", "entity", processingContext);

        verify(eventTransaction).onAppend(any());
        assertEquals("entity", result.entity());
        assertEquals("id", result.identifier());
    }

    @Test
    void persistAlreadyPersistedEntityDoesNotRegisterItToListenToEvents() {
        ProcessingContext processingContext = new StubProcessingContext();

        ManagedEntity<String, String> first = testSubject.persist("id", "entity", processingContext);
        ManagedEntity<String, String> second = testSubject.persist("id", "entity", processingContext);

        verify(eventTransaction).onAppend(any());
        assertSame(first, second);
        assertEquals("entity", first.entity());
        assertEquals("id", first.identifier());
    }

    @Test
    void assigningEntityToOtherProcessingContextInExactFormat() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();
        ProcessingContext processingContext2 = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStore)
                .source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types()
                                                                                          .contains("id")), );

        ManagedEntity<String, String> result = testSubject.load("test", processingContext).get();

        testSubject.attach(result, processingContext2);

        verify(eventTransaction, times(2)).onAppend(any());
    }

    @Test
    void assigningEntityToOtherProcessingContextInOtherFormat() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();
        ProcessingContext processingContext2 = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStore)
                .source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types()
                                                                                          .contains("id")), );

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

        verify(eventTransaction, times(2)).onAppend(any());
    }

    @Test
    void updateLoadedEventSourcedEntity() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStore)
                .source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types()
                                                                                          .contains("id")), );

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        verify(eventStore).source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types()
                                                                                                    .contains("id")), );
        //noinspection unchecked
        ArgumentCaptor<Consumer<EventMessage<?>>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(eventTransaction).onAppend(callback.capture());
        assertEquals("-0-1", result.resultNow().entity());

        callback.getValue().accept(new GenericEventMessage<>("live"));
        assertEquals("-0-1-live", result.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldLoadWhenEventsAreReturned() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(domainEvent(0), domainEvent(1))))
                .when(eventStore)
                .source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types()
                                                                                          .contains("id")), );

        CompletableFuture<ManagedEntity<String, String>> result =
                testSubject.loadOrCreate("test", processingContext, () -> fail("This should not have been invoked"));

        assertEquals("-0-1", result.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldCreateWhenNoEventsAreReturned() {
        StubProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.empty())
                .when(eventStore)
                .source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types()
                                                                                          .contains("id")), );

        CompletableFuture<ManagedEntity<String, String>> loaded =
                testSubject.loadOrCreate("test", processingContext, () -> "created");

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        verify(eventTransaction).onAppend(any());
        verify(eventStore).source(ArgumentMatchers.<SourcingCondition>argThat(condition -> condition.criteria().types()
                                                                                                    .contains("id")), );

        assertEquals("created", loaded.resultNow().entity());
    }

    // TODO - Perfect candidate to move to a commons test utils module
    private static DomainEventMessage<?> domainEvent(int seq) {
        return new GenericDomainEventMessage<>("test", "id", seq, seq);
    }
}