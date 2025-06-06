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

package org.axonframework.eventstreaming;

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.StreamableMessageSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link LegacyStreamableEventSource} class, which it's an adapter for the deprecated
 * {@link StreamableMessageSource}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@ExtendWith(MockitoExtension.class)
class LegacyStreamableEventSourceTest {

    @Mock
    private StreamableMessageSource<EventMessage<?>> mockDelegate;

    @Mock
    private BlockingStream<EventMessage<?>> mockBlockingStream;

    private LegacyStreamableEventSource<EventMessage<?>> testSubject;

    private TrackingToken testToken;
    private StreamingCondition testCondition;
    private EventCriteria tagCriteria;
    private EventMessage<?> testEvent;

    @BeforeEach
    void setUp() {
        testSubject = new LegacyStreamableEventSource<>(mockDelegate);

        // Create real test objects
        testToken = new GlobalSequenceTrackingToken(42L);
        var noCriteria = EventCriteria.havingAnyTag();
        tagCriteria = EventCriteria.havingTags("aggregate", "test-id");
        testCondition = new DefaultStreamingCondition(testToken, noCriteria);

        testEvent = createTestEventMessage("test-payload");
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("should create instance with valid delegate")
        void shouldCreateInstanceWithValidDelegate() {
            // Given - a valid StreamableMessageSource delegate

            // When - creating LegacyStreamableEventSource
            var result = new LegacyStreamableEventSource<>(mockDelegate);

            // Then - instance should be created successfully
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw exception with null delegate")
        void shouldThrowExceptionWithNullDelegate() {
            // Given - a null delegate

            // When & Then - creating LegacyStreamableEventSource should throw
            assertThrows(NullPointerException.class, () ->
                    new LegacyStreamableEventSource<>(null));
        }
    }

    @Nested
    @DisplayName("Token Operations")
    class TokenOperationsTest {

        @Test
        @DisplayName("headToken should delegate to underlying source")
        void headTokenShouldDelegateToUnderlyingSource() {
            // Given - delegate returns a token future
            var expectedToken = new GlobalSequenceTrackingToken(0L);
            when(mockDelegate.headToken()).thenReturn(CompletableFuture.completedFuture(expectedToken));

            // When - calling headToken
            CompletableFuture<TrackingToken> result = testSubject.headToken();

            // Then - should delegate and return the same future
            assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                              .isEqualTo(expectedToken);
            verify(mockDelegate).headToken();
        }

        @Test
        @DisplayName("tailToken should delegate to underlying source")
        void tailTokenShouldDelegateToUnderlyingSource() {
            // Given - delegate returns a token future
            var expectedToken = new GlobalSequenceTrackingToken(100L);
            when(mockDelegate.tailToken()).thenReturn(CompletableFuture.completedFuture(expectedToken));

            // When - calling tailToken
            CompletableFuture<TrackingToken> result = testSubject.tailToken();

            // Then - should delegate and return the same future
            assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                              .isEqualTo(expectedToken);
            verify(mockDelegate).tailToken();
        }

        @Test
        @DisplayName("tokenAt should delegate to underlying source with instant")
        void tokenAtShouldDelegateToUnderlyingSourceWithInstant() {
            // Given - a specific instant and expected token
            var instant = Instant.now();
            var expectedToken = new GlobalSequenceTrackingToken(50L);
            when(mockDelegate.tokenAt(instant)).thenReturn(CompletableFuture.completedFuture(expectedToken));

            // When - calling tokenAt
            CompletableFuture<TrackingToken> result = testSubject.tokenAt(instant);

            // Then - should delegate with the correct instant and return the same future
            assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                              .isEqualTo(expectedToken);
            verify(mockDelegate).tokenAt(eq(instant));
        }

        @Test
        @DisplayName("should handle exceptions in token operations")
        void shouldHandleExceptionsInTokenOperations() {
            // Given - delegate throws exception
            var expectedException = new RuntimeException("Token error");
            when(mockDelegate.headToken()).thenReturn(CompletableFuture.failedFuture(expectedException));

            // When - calling headToken
            CompletableFuture<TrackingToken> result = testSubject.headToken();

            // Then - should propagate the exception
            assertThat(result).failsWithin(Duration.ofSeconds(1))
                              .withThrowableOfType(ExecutionException.class)
                              .withCauseInstanceOf(RuntimeException.class)
                              .withMessageContaining("Token error");
        }
    }

    @Nested
    @DisplayName("Open Stream")
    class OpenStreamTest {

        @BeforeEach
        void setUp() {
            when(mockDelegate.openStream(testToken)).thenReturn(mockBlockingStream);
        }

        @Test
        @DisplayName("should create MessageStream with BlockingStream from delegate")
        void shouldCreateMessageStreamWithBlockingStreamFromDelegate() {
            // Given - condition with position and criteria

            // When - opening stream
            MessageStream<EventMessage<?>> result = testSubject.open(testCondition);

            // Then - should create MessageStream and delegate to the underlying source
            assertThat(result).isNotNull();
            verify(mockDelegate).openStream(eq(testToken));
        }

        @Test
        @DisplayName("should use criteria from condition for filtering")
        void shouldUseCriteriaFromConditionForFiltering() {
            // Given - condition with specific criteria
            var criteriaCondition = new DefaultStreamingCondition(testToken, tagCriteria);

            // When - opening stream
            MessageStream<EventMessage<?>> result = testSubject.open(criteriaCondition);

            // Then - should create stream with criteria
            assertThat(result).isNotNull();
            verify(mockDelegate).openStream(eq(testToken));
        }
    }

    @Nested
    @DisplayName("BlockingMessageStream")
    class BlockingMessageStreamTest {

        private MessageStream<EventMessage<?>> messageStream;

        @BeforeEach
        void setUp() {
            when(mockDelegate.openStream(testToken)).thenReturn(mockBlockingStream);
            messageStream = testSubject.open(testCondition);
        }

        @Nested
        @DisplayName("Next Operation")
        class NextOperationTest {

            @Test
            @DisplayName("should return empty when no messages available")
            void shouldReturnEmptyWhenNoMessagesAvailable() {
                // Given - no messages available in blocking stream
                when(mockBlockingStream.hasNextAvailable()).thenReturn(false);

                // When - calling next
                Optional<MessageStream.Entry<EventMessage<?>>> result = messageStream.next();

                // Then - should return empty
                assertThat(result).isEmpty();
                verify(mockBlockingStream).hasNextAvailable();
            }

            @Test
            @DisplayName("should return entry when message available and no criteria filtering")
            void shouldReturnEntryWhenMessageAvailableAndNoCriteriaFiltering() throws Exception {
                // Given - message available and no criteria (should match all)
                when(mockBlockingStream.hasNextAvailable()).thenReturn(true);
                when(mockBlockingStream.nextAvailable()).thenAnswer(invocation -> testEvent);

                // When - calling next
                Optional<MessageStream.Entry<EventMessage<?>>> result = messageStream.next();

                // Then - should return entry with the message
                assertThat(result).isPresent();
                assertThat(result.get().message()).isEqualTo(testEvent);
                verify(mockBlockingStream).nextAvailable();
            }

            @Test
            @DisplayName("should return entry with tracking token context for TrackedEventMessage")
            void shouldReturnEntryWithTrackingTokenContextForTrackedEventMessage() throws Exception {
                // Given - tracked event message available
                when(mockBlockingStream.hasNextAvailable()).thenReturn(true);
                when(mockBlockingStream.nextAvailable()).thenAnswer(invocation ->
                                                                            createTestTrackedEventMessage(
                                                                                    "tracked-payload",
                                                                                    testToken));

                // When - calling next
                Optional<MessageStream.Entry<EventMessage<?>>> result = messageStream.next();

                // Then - should return entry with tracking token in context
                assertThat(result).isPresent();
                var entry = result.get();
                assertThat(entry.message()).isInstanceOf(TrackedEventMessage.class);
                assertThat(TrackingToken.fromContext(entry)).hasValue(testToken);
                assertThat(entry.getResource(Message.RESOURCE_KEY)).isEqualTo(entry.message());
            }

            @Test
            @DisplayName("should handle InterruptedException gracefully")
            void shouldHandleInterruptedExceptionGracefully() throws Exception {
                // Given - blocking stream throws InterruptedException
                when(mockBlockingStream.hasNextAvailable()).thenReturn(true);
                when(mockBlockingStream.nextAvailable()).thenThrow(new InterruptedException("Test interruption"));

                // When - calling next
                Optional<MessageStream.Entry<EventMessage<?>>> result = messageStream.next();

                // Then - should return an empty and set interrupt flag
                assertThat(result).isEmpty();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();

                // Clean up interrupt flag for other tests
                Thread.interrupted();
            }

            @Test
            @DisplayName("should return empty when nextAvailable returns null")
            void shouldReturnEmptyWhenNextAvailableReturnsNull() throws Exception {
                // Given - nextAvailable returns null
                when(mockBlockingStream.hasNextAvailable()).thenReturn(true);
                when(mockBlockingStream.nextAvailable()).thenAnswer(invocation -> null);

                // When - calling next
                Optional<MessageStream.Entry<EventMessage<?>>> result = messageStream.next();

                // Then - should return empty
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should filter messages based on criteria")
            void shouldFilterMessagesBasedOnCriteria() throws Exception {
                // Given - stream with tag-based criteria that will reject our test event
                var filteringCondition = new DefaultStreamingCondition(testToken, tagCriteria);
                when(mockDelegate.openStream(testToken)).thenReturn(mockBlockingStream);
                var filteringStream = testSubject.open(filteringCondition);

                // Message available but won't match criteria (no tags)
                when(mockBlockingStream.hasNextAvailable()).thenReturn(true, false);
                when(mockBlockingStream.nextAvailable()).thenAnswer(invocation ->
                                                                            createTestEventMessage("test-payload"));

                // When - calling next
                Optional<MessageStream.Entry<EventMessage<?>>> result = filteringStream.next();

                // Then - should return empty because the message doesn't match criteria
                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("Stream Lifecycle")
        class StreamLifecycleTest {

            @Test
            @DisplayName("should delegate hasNextAvailable to blocking stream")
            void shouldDelegateHasNextAvailableToBlockingStream() {
                // Given - blocking stream has messages available
                when(mockBlockingStream.hasNextAvailable()).thenReturn(true);

                // When - checking if has next available
                boolean result = messageStream.hasNextAvailable();

                // Then - should delegate and return result
                assertThat(result).isTrue();
                verify(mockBlockingStream).hasNextAvailable();
            }

            @Test
            @DisplayName("should delegate onAvailable callback to blocking stream")
            void shouldDelegateOnAvailableCallbackToBlockingStream() {
                // Given - a callback runnable
                var callbackExecuted = new AtomicBoolean(false);
                Runnable callback = () -> callbackExecuted.set(true);
                when(mockBlockingStream.setOnAvailableCallback(callback)).thenReturn(true);

                // When - setting callback
                messageStream.onAvailable(callback);

                // Then - should delegate to blocking stream
                verify(mockBlockingStream).setOnAvailableCallback(eq(callback));
            }

            @Test
            @DisplayName("should delegate close to blocking stream")
            void shouldDelegateCloseToBlockingStream() {
                // Given - message stream is open

                // When - closing stream
                messageStream.close();

                // Then - should delegate close to blocking stream
                verify(mockBlockingStream).close();
            }

            @Test
            @DisplayName("should return empty error as BlockingStream doesn't support error reporting")
            void shouldReturnEmptyErrorAsBlockingStreamDoesntSupportErrorReporting() {
                // Given - message stream with no error reporting support

                // When - checking for error
                Optional<Throwable> result = messageStream.error();

                // Then - should return empty
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should consider stream completed when no messages available and peek is empty")
            void shouldConsiderStreamCompletedWhenNoMessagesAvailableAndPeekIsEmpty() {
                // Given - no messages available and peek returns empty
                when(mockBlockingStream.hasNextAvailable()).thenReturn(false);
                when(mockBlockingStream.peek()).thenReturn(Optional.empty());

                // When - checking if completed
                boolean result = messageStream.isCompleted();

                // Then - should return true
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should not consider stream completed when messages available")
            void shouldNotConsiderStreamCompletedWhenMessagesAvailable() {
                // Given - messages available
                when(mockBlockingStream.hasNextAvailable()).thenReturn(true);

                // When - checking if completed
                boolean result = messageStream.isCompleted();

                // Then - should return false
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("Real Streaming Simulation")
        class RealStreamingSimulationTest {

            @Test
            @DisplayName("should handle multiple messages in sequence")
            void shouldHandleMultipleMessagesInSequence() throws Exception {
                // Given - multiple messages available in sequence
                var callCount = new AtomicInteger(0);
                when(mockBlockingStream.hasNextAvailable()).thenAnswer(invocation -> callCount.get() < 3);
                when(mockBlockingStream.nextAvailable()).thenAnswer(invocation -> {
                    int count = callCount.getAndIncrement();
                    return switch (count) {
                        case 0 -> createTestEventMessage("payload-1");
                        case 1 -> createTestTrackedEventMessage("payload-2", new GlobalSequenceTrackingToken(10L));
                        case 2 -> createTestEventMessage("payload-3");
                        default -> null;
                    };
                });

                // When - consuming all messages
                var result1 = messageStream.next();
                var result2 = messageStream.next();
                var result3 = messageStream.next();
                var result4 = messageStream.next(); // Should be empty

                // Then - should return all messages in order
                assertThat(result1).isPresent();
                assertThat(result1.get().message().getPayload()).isEqualTo("payload-1");

                assertThat(result2).isPresent();
                assertThat(result2.get().message().getPayload()).isEqualTo("payload-2");
                assertThat(TrackingToken.fromContext(result2.get())).isPresent();
                assertThat(TrackingToken.fromContext(result2.get()).get())
                        .isEqualTo(new GlobalSequenceTrackingToken(10L));

                assertThat(result3).isPresent();
                assertThat(result3.get().message().getPayload()).isEqualTo("payload-3");

                assertThat(result4).isEmpty(); // No more messages
            }
        }
    }

    private EventMessage<?> createTestEventMessage(String payload) {
        return EventTestUtils.asEventMessage(payload);
    }

    private TrackedEventMessage<?> createTestTrackedEventMessage(String payload, TrackingToken token) {
        return new GenericTrackedEventMessage<>(token, createTestEventMessage(payload));
    }
}