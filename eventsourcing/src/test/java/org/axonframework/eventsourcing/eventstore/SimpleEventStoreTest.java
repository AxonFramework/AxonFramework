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

import junit.framework.AssertionFailedError;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleEventStore} supports just one context and delegates operations to
 * {@link AsyncEventStorageEngine}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimpleEventStoreTest {

    private static final String MATCHING_CONTEXT = "matching-context";
    private static final String NOT_MATCHING_CONTEXT = "not-matching-context";

    private SimpleEventStore testSubject;
    private AsyncEventStorageEngine mockStorageEngine;
    private StubProcessingContext processingContext;

    @BeforeEach
    void setUp() {
        mockStorageEngine = mock(AsyncEventStorageEngine.class);
        processingContext = new StubProcessingContext();
        testSubject = new SimpleEventStore(mockStorageEngine, MATCHING_CONTEXT, m -> Collections.emptySet());
    }

    private static GlobalSequenceTrackingToken aGlobalSequenceToken() {
        return new GlobalSequenceTrackingToken(999);
    }

    private static StreamingCondition aStreamingCondition() {
        return StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(999));
    }

    @Nested
    class VerifyingContext {

        @Test
        void transactionThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.transaction(processingContext, NOT_MATCHING_CONTEXT));
        }

        @Test
        void publishThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.publish(processingContext, NOT_MATCHING_CONTEXT, eventMessage(0)));
        }

        @Test
        void publishWithoutProcessingContextThrowsUnsupportedOperationException() {
            assertThrows(UnsupportedOperationException.class,
                         () -> testSubject.publish(NOT_MATCHING_CONTEXT, eventMessage(0)));
        }

        @Test
        void openThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.open(NOT_MATCHING_CONTEXT, aStreamingCondition()));
        }

        @Test
        void headTokenThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class, () -> testSubject.headToken(NOT_MATCHING_CONTEXT));
        }

        @Test
        void tailTokenThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class, () -> testSubject.tailToken(NOT_MATCHING_CONTEXT));
        }

        @Test
        void tokenAtThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.tokenAt(NOT_MATCHING_CONTEXT, Instant.now()));
        }

        @Test
        void transactionDoesNotThrowIfContextMatches() {
            assertDoesNotThrow(() -> testSubject.transaction(processingContext, MATCHING_CONTEXT));
        }

        @Test
        void publishDoesNotThrowIfContextMatches() {
            ProcessingContext mockProcessingContext = mock(ProcessingContext.class);
            when(mockProcessingContext.computeResourceIfAbsent(any(),
                                                               any())).thenReturn(mock(EventStoreTransaction.class));

            assertDoesNotThrow(() -> testSubject.publish(mockProcessingContext, MATCHING_CONTEXT, eventMessage(0)));
        }

        @Test
        void openDoesNotThrowIfContextMatches() {
            assertDoesNotThrow(() -> testSubject.open(MATCHING_CONTEXT, aStreamingCondition()));
        }

        @Test
        void headTokenDoesNotThrowIfContextMatches() {
            assertDoesNotThrow(() -> testSubject.headToken(MATCHING_CONTEXT));
        }

        @Test
        void tailTokenDoesNotThrowIfContextMatches() {
            assertDoesNotThrow(() -> testSubject.tailToken(MATCHING_CONTEXT));
        }

        @Test
        void tokenAtDoesNotThrowIfContextMatches() {
            assertDoesNotThrow(() -> testSubject.tokenAt(MATCHING_CONTEXT, Instant.now()));
        }
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
            MessageStream<EventMessage<?>> result = testSubject.open(MATCHING_CONTEXT, condition);

            // then
            assertSame(expectedStream, result);
            verify(mockStorageEngine).stream(condition);
        }

        @Test
        void headTokenDelegatesToStorageEngine() {
            // given
            CompletableFuture<TrackingToken> expectedFuture = completedFuture(aGlobalSequenceToken());
            when(mockStorageEngine.headToken()).thenReturn(expectedFuture);

            // when
            CompletableFuture<TrackingToken> result = testSubject.headToken(MATCHING_CONTEXT);

            // then
            assertSame(expectedFuture, result);
            verify(mockStorageEngine).headToken();
        }

        @Test
        void tailTokenDelegatesToStorageEngine() {
            // given
            CompletableFuture<TrackingToken> expectedFuture = completedFuture(aGlobalSequenceToken());
            when(mockStorageEngine.tailToken()).thenReturn(expectedFuture);

            // when
            CompletableFuture<TrackingToken> result = testSubject.tailToken(MATCHING_CONTEXT);

            // then
            assertSame(expectedFuture, result);
            verify(mockStorageEngine).tailToken();
        }

        @Test
        void tokenAtDelegatesToStorageEngine() {
            // given
            Instant timestamp = Instant.now();
            CompletableFuture<TrackingToken> expectedFuture = completedFuture(aGlobalSequenceToken());
            when(mockStorageEngine.tokenAt(timestamp)).thenReturn(expectedFuture);

            // when
            CompletableFuture<TrackingToken> result = testSubject.tokenAt(MATCHING_CONTEXT, timestamp);

            // then
            assertSame(expectedFuture, result);
            verify(mockStorageEngine).tokenAt(timestamp);
        }
    }

    @Nested
    class TransactionalAppend {

        @Test
        void appendingWithoutReadMustUseInfinityConsistencyMarker() throws Exception {
            AsyncEventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            GlobalIndexConsistencyMarker markerAfterCommit = new GlobalIndexConsistencyMarker(42);

            AsyncUnitOfWork asyncUnitOfWork = new AsyncUnitOfWork();
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(markerAfterCommit));
            var result = asyncUnitOfWork.executeWithResult(pc -> {
                EventStoreTransaction transaction = testSubject.transaction(pc, MATCHING_CONTEXT);
                transaction.appendEvent(eventMessage(0));
                return completedFuture(transaction);
            });
            var newAppendPosition = result.get(5, TimeUnit.SECONDS).appendPosition();

            assertSame(newAppendPosition, markerAfterCommit);
            verify(mockStorageEngine).appendEvents(argThat(c -> ConsistencyMarker.INFINITY.equals(c.consistencyMarker())),
                                                   anyList());
        }

        @Test
        void appendingAfterReadsUpdatesTheAppendCondition() throws Exception {
            AsyncEventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            GlobalIndexConsistencyMarker markerAfterCommit = new GlobalIndexConsistencyMarker(42);

            AsyncUnitOfWork asyncUnitOfWork = new AsyncUnitOfWork();
            when(mockStorageEngine.source(any())).thenReturn(messageStreamOf(10));
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(markerAfterCommit));

            var result = asyncUnitOfWork.executeWithResult(pc -> {
                EventStoreTransaction transaction = testSubject.transaction(pc, MATCHING_CONTEXT);
                doConsumeAll(transaction.source(SourcingCondition.conditionFor(EventCriteria.forAnyEventType()
                                                                                            .withAnyTags())));
                transaction.appendEvent(eventMessage(0));
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
            AsyncEventStorageEngine.AppendTransaction mockAppendTransaction = mock();
            GlobalIndexConsistencyMarker markerAfterCommit = new GlobalIndexConsistencyMarker(101);

            AsyncUnitOfWork asyncUnitOfWork = new AsyncUnitOfWork();
            when(mockStorageEngine.source(any())).thenReturn(messageStreamOf(size1))
                                                 .thenReturn(messageStreamOf(size2))
                                                 .thenReturn(messageStreamOf(size3));
            when(mockStorageEngine.appendEvents(any(), anyList())).thenReturn(completedFuture(mockAppendTransaction));
            when(mockAppendTransaction.commit()).thenReturn(completedFuture(markerAfterCommit));
            var result = asyncUnitOfWork.executeWithResult(pc -> {
                EventStoreTransaction transaction = testSubject.transaction(pc, MATCHING_CONTEXT);
                var firstStream = transaction.source(SourcingCondition.conditionFor(EventCriteria.forAnyEventType()
                                                                                                 .withAnyTags()));

                var secondStream = transaction.source(SourcingCondition.conditionFor(EventCriteria.forAnyEventType()
                                                                                                  .withAnyTags()));
                var thirdStream = transaction.source(SourcingCondition.conditionFor(EventCriteria.forAnyEventType()
                                                                                                 .withAnyTags()));
                doConsumeAll(firstStream, secondStream, thirdStream);
                transaction.appendEvent(eventMessage(0));
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

    @Test
    void describeToDescribesPropertiesForEventStorageEngineAndTheContext() {
        // given
        ComponentDescriptor descriptor = mock(ComponentDescriptor.class);

        // when
        testSubject.describeTo(descriptor);

        // then
        verify(descriptor).describeProperty("eventStorageEngine", mockStorageEngine);
        verify(descriptor).describeProperty("context", MATCHING_CONTEXT);
    }

    private static @NotNull MessageStream<EventMessage<?>> messageStreamOf(int messageCount) {
        return MessageStream.fromStream(IntStream.range(0, messageCount).boxed(),
                                        SimpleEventStoreTest::eventMessage,
                                        i -> Context.with(ConsistencyMarker.RESOURCE_KEY,
                                                          new GlobalIndexConsistencyMarker(i)));
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


    // TODO - Discuss: @Steven - Perfect candidate to move to a commons test utils module?
    private static EventMessage<?> eventMessage(int seq) {
        return EventTestUtils.asEventMessage("Event[" + seq + "]");
    }
}