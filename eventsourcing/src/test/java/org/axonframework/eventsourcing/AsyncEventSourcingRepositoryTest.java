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

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.AppendEventTransaction;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncEventSourcingRepositoryTest {

    private AsyncEventSourcingRepository<String, String> testSubject;
    private EventStore mockEventStore;
    private AppendEventTransaction eventTransaction;

    @BeforeEach
    void setUp() {
        mockEventStore = mock();
        eventTransaction = mock();
        testSubject = new AsyncEventSourcingRepository<>(mockEventStore,
                                                         (event, value) -> value + "-" + event.getPayload(),
                                                         c -> "id");

        when(mockEventStore.currentTransaction(any())).thenReturn(eventTransaction);
    }

    @Test
    void loadEventSourcedEntity() {
        when(mockEventStore.readEvents("id")).thenReturn(DomainEventStream.of(domainEvent(0), domainEvent(1)));
        StubProcessingContext processingContext = new StubProcessingContext();
        CompletableFuture<ManagedEntity<String, String>> loaded = testSubject.load(
                "test",
                processingContext);

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        verify(eventTransaction).onEvent(any());
        verify(mockEventStore).readEvents(eq("id"));

        assertEquals("null-0-1", loaded.resultNow().entity());
    }

    @Test
    void persistNewEntityRegistersItToListenToEvents() {
        StubProcessingContext processingContext = new StubProcessingContext();

        ManagedEntity<String, String> actual = testSubject.persist("id", "entity", processingContext);

        verify(eventTransaction).onEvent(any());
        assertEquals("entity", actual.entity());
        assertEquals("id", actual.identifier());
    }

    @Test
    void persistAlreadyPersistedEntityDoesNotRegisterItToListenToEvents() {
        StubProcessingContext processingContext = new StubProcessingContext();

        ManagedEntity<String, String> actual = testSubject.persist("id", "entity", processingContext);
        ManagedEntity<String, String> second = testSubject.persist("id", "entity", processingContext);

        verify(eventTransaction).onEvent(any());
        assertSame(actual, second);
        assertEquals("entity", actual.entity());
        assertEquals("id", actual.identifier());
    }

    @Test
    void assigningEntityToOtherProcessingContextInExactFormat() throws Exception {
        when(mockEventStore.readEvents("id")).thenReturn(DomainEventStream.of(domainEvent(0), domainEvent(1)));
        StubProcessingContext processingContext = new StubProcessingContext();
        StubProcessingContext processingContext2 = new StubProcessingContext();
        ManagedEntity<String, String> loaded = testSubject.load("test", processingContext).get();

        testSubject.attach(loaded, processingContext2);

        verify(eventTransaction, times(2)).onEvent(any());
    }

    @Test
    void assigningEntityToOtherProcessingContextInOtherFormat() throws Exception {
        when(mockEventStore.readEvents("id")).thenReturn(DomainEventStream.of(domainEvent(0), domainEvent(1)));
        StubProcessingContext processingContext = new StubProcessingContext();
        StubProcessingContext processingContext2 = new StubProcessingContext();
        ManagedEntity<String, String> loaded = testSubject.load("test", processingContext).get();

        testSubject.attach(new ManagedEntity<>() {
            @Override
            public String identifier() {
                return loaded.identifier();
            }

            @Override
            public String entity() {
                return loaded.entity();
            }

            @Override
            public String applyStateChange(UnaryOperator<String> change) {
                fail("This should not have been invoked");
                return "ERROR";
            }
        }, processingContext2);

        verify(eventTransaction, times(2)).onEvent(any());
    }

    @Test
    void updateLoadedEventSourcedEntity() {
        when(mockEventStore.readEvents("id")).thenReturn(DomainEventStream.of(domainEvent(0), domainEvent(1)));
        StubProcessingContext processingContext = new StubProcessingContext();
        CompletableFuture<ManagedEntity<String, String>> loaded = testSubject.load(
                "test",
                processingContext);

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        ArgumentCaptor<Consumer<EventMessage<?>>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(mockEventStore).readEvents(eq("id"));
        verify(eventTransaction).onEvent(callback.capture());
        assertEquals("null-0-1", loaded.resultNow().entity());


        callback.getValue().accept(new GenericEventMessage<>("live"));

        assertEquals("null-0-1-live", loaded.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldLoadWhenEventsAreReturned() {
        when(mockEventStore.readEvents("id")).thenReturn(DomainEventStream.of(domainEvent(0), domainEvent(1)));
        StubProcessingContext processingContext = new StubProcessingContext();
        CompletableFuture<ManagedEntity<String, String>> loaded = testSubject.loadOrCreate(
                "test",
                processingContext,
                () -> fail("This should not have been invoked"));

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        verify(eventTransaction).onEvent(any());
        verify(mockEventStore).readEvents(eq("id"));

        assertEquals("null-0-1", loaded.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldCreateWhenNoEventsAreReturned() {
        when(mockEventStore.readEvents("id")).thenReturn(DomainEventStream.empty());
        StubProcessingContext processingContext = new StubProcessingContext();
        CompletableFuture<ManagedEntity<String, String>> loaded = testSubject.loadOrCreate(
                "test",
                processingContext,
                () -> "created");

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        verify(eventTransaction).onEvent(any());
        verify(mockEventStore).readEvents(eq("id"));

        assertEquals("created", loaded.resultNow().entity());
    }

    private DomainEventMessage<?> domainEvent(int seq) {
        return new GenericDomainEventMessage<>("test", "id", seq, seq);
    }
}