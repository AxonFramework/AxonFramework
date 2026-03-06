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

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.GlobalIndexPosition;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.snapshot.api.EvolutionResult;
import org.axonframework.eventsourcing.snapshot.api.Snapshot;
import org.axonframework.eventsourcing.snapshot.api.Snapshotter;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.modelling.repository.ManagedEntity;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.eventhandling.EventTestUtils.createEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventSourcingRepository}.
 *
 * @author Allard Buijze
 */
@ExtendWith(MockitoExtension.class)
class EventSourcingRepositoryTest {

    private static final Set<Tag> TEST_TAGS = Set.of(new Tag("aggregateId", "id"));
    private static final EventCriteria TEST_CRITERIA = EventCriteria.havingTags("aggregateId", "id");

    private EventStore eventStore = mock();
    private EventStoreTransaction eventStoreTransaction = mock();
    private Snapshotter<String, String> snapshotter = mock();
    private EventSourcedEntityFactory<String, String> factory;

    private EventSourcingRepository<String, String> testSubject;

    @BeforeEach
    void setUp() {
        when(eventStore.transaction(any())).thenReturn(eventStoreTransaction);

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
                (entity, event, context) -> entity + "-" + event.payload(),
                snapshotter
        );
    }

    @Test
    void loadEventSourcedEntity() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally(), () -> result.exceptionNow().toString());
        verify(eventStore, times(2)).transaction(processingContext);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());
        verify(snapshotter).load("test");

        assertEquals("test(0)-0-1", result.resultNow().entity());
    }

    @Test
    void loadEventSourcedEntityFromSnapshot(@Captor ArgumentCaptor<EvolutionResult> evolutionResult) {
        ProcessingContext processingContext = new StubProcessingContext();
        EventMessage event1 = createEvent(0);
        EventMessage event2 = createEvent(1);

        doAnswer(m -> {
            @SuppressWarnings("unchecked")
            Consumer<Position> consumer = (Consumer<Position>)m.getArgument(1);

            consumer.accept(new GlobalIndexPosition(10));

            return MessageStream.fromStream(Stream.of(event1, event2));
        })
        .when(eventStoreTransaction)
        .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

        when(snapshotter.load("test")).thenReturn(CompletableFuture.completedFuture(new Snapshot(
            new GlobalIndexPosition(5),
            "1",
            "payload",
            Instant.now(),
            Map.of()
        )));

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally(), () -> result.exceptionNow().toString());
        verify(eventStore, times(2)).transaction(processingContext);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());
        verify(snapshotter).load("test");
        verify(snapshotter).onEventApplied("test", "payload-0", event1);
        verify(snapshotter).onEventApplied("test", "payload-0-1", event2);
        verify(snapshotter).onEvolutionCompleted(
            eq("test"),
            eq("payload-0-1"),
            eq(new GlobalIndexPosition(10)),
            evolutionResult.capture()
        );

        assertThat(evolutionResult.getValue().eventsApplied()).isEqualTo(2);
        assertThat(evolutionResult.getValue().sourcingTime()).isLessThan(Duration.ofSeconds(1));
        assertThat(evolutionResult.getValue().snapshotRequested()).isFalse();

        assertEquals("payload-0-1", result.resultNow().entity());
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
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

        ManagedEntity<String, String> result = testSubject.load("test", processingContext).get();

        testSubject.attach(result, processingContext2);

        verify(eventStoreTransaction, times(2)).onAppend(any());
        verify(snapshotter).load("test");
    }

    @Test
    void assigningEntityToOtherProcessingContextInOtherFormat() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();
        ProcessingContext processingContext2 = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

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
            public String applyStateChange(@NonNull UnaryOperator<String> change) {
                fail("This should not have been invoked");
                return "ERROR";
            }
        }, processingContext2);

        verify(eventStoreTransaction, times(2)).onAppend(any());
        verify(snapshotter).load("test");
    }

    @Test
    void updateLoadedEventSourcedEntity() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.fromStream(Stream.of(createEvent(0), createEvent(1))))
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        verify(eventStore, times(2)).transaction(processingContext);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());
        verify(snapshotter).load("test");

        @SuppressWarnings("unchecked")
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
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

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
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

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
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

        CompletableFuture<ManagedEntity<String, String>> result = testSubject.load("test", processingContext);

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(EntityMissingAfterFirstEventException.class, result.exceptionNow());
    }

    @Test
    void loadShouldReturnNullEntityWhenNoEventsAreReturned() {
        StubProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.empty())
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

        CompletableFuture<ManagedEntity<String, String>> loaded =
                testSubject.load("test", processingContext);

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        verify(eventStore, times(2)).transaction(processingContext);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());
        verify(snapshotter).load("test");

        assertNull(loaded.resultNow().entity());
    }

    @Test
    void loadOrCreateShouldReturnNoEventMessageConstructorEntityWhenNoEventsAreReturned() {
        ProcessingContext processingContext = new StubProcessingContext();
        doReturn(MessageStream.empty())
                .when(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());

        CompletableFuture<ManagedEntity<String, String>> loaded =
                testSubject.loadOrCreate("test", processingContext);

        assertTrue(loaded.isDone());
        assertFalse(loaded.isCompletedExceptionally());
        verify(eventStore, times(2)).transaction(processingContext);
        verify(eventStoreTransaction).onAppend(any());
        verify(eventStoreTransaction)
                .source(argThat(EventSourcingRepositoryTest::conditionPredicate), any());
        verify(snapshotter).load("test");

        assertEquals("test()", loaded.resultNow().entity());
    }

    private static boolean conditionPredicate(SourcingCondition condition) {
        return condition.matches(new QualifiedName("ignored"), TEST_TAGS);
    }
}