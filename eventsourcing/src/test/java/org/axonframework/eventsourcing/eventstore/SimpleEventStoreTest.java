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

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.StubProcessingContext;
import org.axonframework.messaging.MessageStream;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

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
        testSubject = new SimpleEventStore(mockStorageEngine, MATCHING_CONTEXT);
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
        void openThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.open(NOT_MATCHING_CONTEXT, aStreamingCondition()));
        }

        @Test
        void headTokenThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.headToken(NOT_MATCHING_CONTEXT));
        }

        @Test
        void tailTokenThrowsIfContextDoesNotMatch() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.tailToken(NOT_MATCHING_CONTEXT));
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
            CompletableFuture<TrackingToken> expectedFuture = CompletableFuture.completedFuture(aGlobalSequenceToken());
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
            CompletableFuture<TrackingToken> expectedFuture = CompletableFuture.completedFuture(aGlobalSequenceToken());
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
            CompletableFuture<TrackingToken> expectedFuture = CompletableFuture.completedFuture(aGlobalSequenceToken());
            when(mockStorageEngine.tokenAt(timestamp)).thenReturn(expectedFuture);

            // when
            CompletableFuture<TrackingToken> result = testSubject.tokenAt(MATCHING_CONTEXT, timestamp);

            // then
            assertSame(expectedFuture, result);
            verify(mockStorageEngine).tokenAt(timestamp);
        }
    }

    @Test
    void describeToIncludesContextAndStorageEngine() {
        // given
        ComponentDescriptor descriptor = mock(ComponentDescriptor.class);

        // when
        testSubject.describeTo(descriptor);

        // then
        verify(descriptor).describeProperty("eventStorageEngine", mockStorageEngine);
        verify(descriptor).describeProperty("context", MATCHING_CONTEXT);
    }
}