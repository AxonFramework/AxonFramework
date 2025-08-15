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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import junit.framework.AssertionFailedError;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.eventhandling.EventTestUtils.createEvent;
import static org.axonframework.utils.AssertUtils.awaitSuccessfulCompletion;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleEventStore} supports just one context and delegates operations to
 * {@link EventStorageEngine}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimpleEventStoreTest {

    private EventStorageEngine mockStorageEngine;
    private TagResolver tagResolver;

    private SimpleEventStore testSubject;

    @BeforeEach
    void setUp() {
        mockStorageEngine = mock(EventStorageEngine.class);
        tagResolver = mock(TagResolver.class);

        testSubject = new SimpleEventStore(mockStorageEngine, tagResolver);
    }

    private static GlobalSequenceTrackingToken aGlobalSequenceToken() {
        return new GlobalSequenceTrackingToken(999);
    }

    private static StreamingCondition aStreamingCondition() {
        return StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(999));
    }

    @Nested
    class DelegatingToStorageEngine {

        @Test
        void openStreamDelegatesConditionToStorageEngine() {
            // given
            StreamingCondition condition = aStreamingCondition();
            MessageStream<EventMessage<?>> expectedStream = mock(MessageStream.class);
            when(mockStorageEngine.stream(condition)).thenReturn(expectedStream);

            // when
            MessageStream<EventMessage<?>> result = testSubject.open(condition);

            // then
            assertSame(expectedStream, result);
            verify(mockStorageEngine).stream(condition);
        }

        @Test
        void firstTokenDelegatesToStorageEngine() {
            // given
            CompletableFuture<TrackingToken> expectedFuture = completedFuture(aGlobalSequenceToken());
            when(mockStorageEngine.firstToken()).thenReturn(expectedFuture);

            // when
            CompletableFuture<TrackingToken> result = testSubject.firstToken();

            // then
            assertSame(expectedFuture, result);
            verify(mockStorageEngine).firstToken();
        }

        @Test
        void latestTokenDelegatesToStorageEngine() {
            // given
            CompletableFuture<TrackingToken> expectedFuture = completedFuture(aGlobalSequenceToken());
            when(mockStorageEngine.latestToken()).thenReturn(expectedFuture);

            // when
            CompletableFuture<TrackingToken> result = testSubject.latestToken();

            // then
            assertSame(expectedFuture, result);
            verify(mockStorageEngine).latestToken();
        }

        @Test
        void tokenAtDelegatesToStorageEngine() {
            // given
            Instant timestamp = Instant.now();
            CompletableFuture<TrackingToken> expectedFuture = completedFuture(aGlobalSequenceToken());
            when(mockStorageEngine.tokenAt(timestamp)).thenReturn(expectedFuture);

            // when
            CompletableFuture<TrackingToken> result = testSubject.tokenAt(timestamp);

            // then
            assertSame(expectedFuture, result);
            verify(mockStorageEngine).tokenAt(timestamp);
        }
    }

    @Nested
    class TransactionalAppend {

        @Test
        void appendingWithoutReadMustUseInfinityConsistencyMarker() throws Exception {
            EventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            GlobalIndexConsistencyMarker markerAfterCommit = new GlobalIndexConsistencyMarker(42);

            UnitOfWork unitOfWork = aUnitOfWork();
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(markerAfterCommit));
            var result = unitOfWork.executeWithResult(pc -> {
                EventStoreTransaction transaction = testSubject.transaction(pc);
                transaction.appendEvent(createEvent(0));
                return completedFuture(transaction);
            });
            var newAppendPosition = result.get(5, TimeUnit.SECONDS).appendPosition();

            assertSame(newAppendPosition, markerAfterCommit);
            verify(mockStorageEngine).appendEvents(argThat(c -> ConsistencyMarker.INFINITY.equals(c.consistencyMarker())),
                                                   anyList());
        }

        @Test
        void appendingAfterReadsUpdatesTheAppendCondition() throws Exception {
            EventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            GlobalIndexConsistencyMarker markerAfterCommit = new GlobalIndexConsistencyMarker(42);

            UnitOfWork unitOfWork = aUnitOfWork();
            when(mockStorageEngine.source(any())).thenReturn(messageStreamOf(10));
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(markerAfterCommit));

            var result = unitOfWork.executeWithResult(pc -> {
                EventStoreTransaction transaction = testSubject.transaction(pc);
                doConsumeAll(transaction.source(SourcingCondition.conditionFor(EventCriteria.havingAnyTag())));
                transaction.appendEvent(createEvent(0));
                return completedFuture(transaction);
            });

            var newAppendPosition = result.get(5, TimeUnit.SECONDS).appendPosition();

            assertSame(newAppendPosition, markerAfterCommit);
            verify(mockStorageEngine).appendEvents(argThat(c -> c.consistencyMarker()
                                                                 .equals(new GlobalIndexConsistencyMarker(9))),
                                                   anyList());
        }

        static Stream<Arguments> generateRandomNumbers() {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            return Stream.iterate(0, i -> i < 5, i -> i + 1)
                         .map(i -> Arguments.arguments(rnd.nextInt(1, 10),
                                                       rnd.nextInt(1, 10),
                                                       rnd.nextInt(1, 10)));
        }

        @ParameterizedTest
        @MethodSource("generateRandomNumbers")
        void readingMultipleTimesShouldKeepTheConsistencyMarkerAtTheSmallestPosition(int size1, int size2, int size3)
                throws Exception {
            EventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            GlobalIndexConsistencyMarker markerAfterCommit = new GlobalIndexConsistencyMarker(101);

            UnitOfWork unitOfWork = aUnitOfWork();
            when(mockStorageEngine.source(any())).thenReturn(messageStreamOf(size1))
                                                 .thenReturn(messageStreamOf(size2))
                                                 .thenReturn(messageStreamOf(size3));
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(markerAfterCommit));
            var result = unitOfWork.executeWithResult(pc -> {
                EventStoreTransaction transaction = testSubject.transaction(pc);
                var firstStream = transaction.source(SourcingCondition.conditionFor(EventCriteria.havingAnyTag()));

                var secondStream = transaction.source(SourcingCondition.conditionFor(EventCriteria.havingAnyTag()));
                var thirdStream = transaction.source(SourcingCondition.conditionFor(EventCriteria.havingAnyTag()));
                doConsumeAll(firstStream, secondStream, thirdStream);
                transaction.appendEvent(createEvent(0));
                return completedFuture(transaction);
            });
            var newAppendPosition = result.get(5, TimeUnit.SECONDS).appendPosition();

            assertSame(newAppendPosition, markerAfterCommit);
            verify(mockStorageEngine).appendEvents(argThat(c -> c.consistencyMarker()
                                                                 .equals(new GlobalIndexConsistencyMarker(
                                                                         Math.min(Math.min(size1, size2), size3) - 1))),
                                                   anyList());
        }
    }

    @Nested
    class Publish {

        @Test
        void publishUsesTheGivenContextToInvokeTheTransactionInCompletingTheReturnedFutureImmediately() {
            EventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(mock(ConsistencyMarker.class)));
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));

            EventMessage<?> testEventZero = createEvent(0);
            EventMessage<?> testEventOne = createEvent(1);

            UnitOfWork uow = aUnitOfWork();
            uow.onPreInvocation(context -> {
                   CompletableFuture<Void> result = testSubject.publish(context, testEventZero, testEventOne);
                   assertTrue(result.isDone());
                   assertFalse(result.isCompletedExceptionally());
                   return result;
               })
               .runOnInvocation(context -> verifyNoInteractions(mockStorageEngine))
               .runOnCommit(context -> verify(mockStorageEngine).appendEvents(any(), anyList()));

            awaitSuccessfulCompletion(uow.execute());
        }

        @Test
        void publishInvokeEventStorageEngineRightAwayWithAppendConditionNone() {
            // given...
            EventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(mock(ConsistencyMarker.class)));
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));
            Tag testTag = new Tag("id", "value");
            when(tagResolver.resolve(any())).thenReturn(Set.of(testTag));
            EventMessage<?> testEvent = createEvent(0);
            //noinspection unchecked
            ArgumentCaptor<List<TaggedEventMessage<?>>> eventCaptor = ArgumentCaptor.forClass(List.class);

            // when...
            CompletableFuture<Void> result = testSubject.publish(null, testEvent);

            // then...
            awaitSuccessfulCompletion(result);
            verify(mockStorageEngine).appendEvents(eq(AppendCondition.none()), eventCaptor.capture());
            List<TaggedEventMessage<?>> capturedEvents = eventCaptor.getValue();
            assertEquals(1, capturedEvents.size());
            TaggedEventMessage<?> resultEvent = capturedEvents.getFirst();
            assertEquals(testEvent, resultEvent.event());
            List<Tag> resultTags = resultEvent.tags().stream().toList();
            assertEquals(1, resultTags.size());
            assertEquals(testTag, resultTags.getFirst());
        }

    }
    @Test
    void describeToDescribesPropertiesForEventStorageEngineAndTheContext() {
        // given
        ComponentDescriptor descriptor = mock(ComponentDescriptor.class);

        // when
        testSubject.describeTo(descriptor);

        // then
        verify(descriptor).describeProperty("eventStorageEngine", mockStorageEngine);
    }

    private static @Nonnull MessageStream<EventMessage<?>> messageStreamOf(int messageCount) {
        return MessageStream.fromStream(
                IntStream.range(0, messageCount).boxed(),
                EventTestUtils::createEvent,
                i -> ConsistencyMarker.addToContext(Context.empty(), new GlobalIndexConsistencyMarker(i))
        );
    }

    @SafeVarargs
    private void doConsumeAll(MessageStream<? extends EventMessage<?>>... sources) {
        try {
            for (MessageStream<? extends EventMessage<?>> source : sources) {
                source.reduce(new Object(), (o, m) -> m)
                      .get(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            AssertionFailedError assertionFailedError = new AssertionFailedError(
                    "Expected to be able to read from message stream");
            assertionFailedError.addSuppressed(e);
            throw assertionFailedError;
        }
    }

    @Nonnull
    private static UnitOfWork aUnitOfWork() {
        return new SimpleUnitOfWorkFactory().create();
    }
}