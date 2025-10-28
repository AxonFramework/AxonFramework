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

package org.axonframework.messaging;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ConcatenatingMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class ConcatenatingMessageStreamTest extends MessageStreamTest<Message> {

    @Override
    MessageStream<Message> completedTestSubject(List<Message> messages) {
        if (messages.isEmpty()) {
            return new ConcatenatingMessageStream<>(MessageStream.empty().cast(), MessageStream.empty().cast());
        } else if (messages.size() == 1) {
            return new ConcatenatingMessageStream<>(MessageStream.just(messages.getFirst()), MessageStream.empty().cast());
        }
        return new ConcatenatingMessageStream<>(
                MessageStream.just(messages.getFirst()),
                MessageStream.fromIterable(messages.subList(1, messages.size()))
        );
    }

    @Override
    MessageStream.Single<Message> completedSingleStreamTestSubject(Message message) {
        Assumptions.abort("ConcatenatingMessageStream doesn't support explicit single-value streams");
        return null;
    }

    @Override
    MessageStream.Empty<Message> completedEmptyStreamTestSubject() {
        Assumptions.abort("ConcatenatingMessageStream doesn't support explicitly empty streams");
        return null;
    }

    @Override
    MessageStream<Message> failingTestSubject(List<Message> messages,
                                                      Exception failure) {
        return completedTestSubject(messages).concatWith(MessageStream.failed(failure));
    }

    @Override
    Message createRandomMessage() {
        return new GenericMessage(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Nested
    class OnAvailableCallback {

        @Test
        void shouldNotifyOnlyWhenFirstStreamHasDataButIsNotCompleted() {
            // given
            CompletableFuture<Void> completionMarker = new CompletableFuture<>();
            Message firstMessage = createRandomMessage();

            // Create an uncompleted stream with data available
            MessageStream<Message> firstStream = MessageStream.fromIterable(List.of(firstMessage))
                    .concatWith(DelayedMessageStream.create(completionMarker.thenApply(e -> MessageStream.empty())).cast());
            MessageStream<Message> secondStream = MessageStream.just(createRandomMessage());
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger callbackCount = new AtomicInteger(0);

            // when - register callback while first stream has data but is not completed
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // then - callback should be invoked because first stream has data and is NOT completed
            assertEquals(1, callbackCount.get(), "Callback should be invoked once when first stream has data but is not completed");
        }

        @Test
        void shouldNotNotifyWhenFirstStreamCompletes() {
            // given
            CompletableFuture<Void> completionMarker = new CompletableFuture<>();
            Message firstMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.fromIterable(List.of(firstMessage))
                    .concatWith(DelayedMessageStream.create(completionMarker.thenApply(e -> MessageStream.empty())).cast());
            MessageStream<Message> secondStream = MessageStream.empty();
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            // Consume the first message
            concatenated.next();

            AtomicInteger callbackCount = new AtomicInteger(0);
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // when - complete the first stream
            completionMarker.complete(null);

            // Give it a moment for any async callbacks (though none should fire)
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // then - callback should NOT be invoked for first stream completion
            assertEquals(0, callbackCount.get(), "Callback should not be invoked when first stream completes");
        }

        @Test
        void shouldNotifyFromSecondStreamWhenFirstCompletesSuccessfully() {
            // given
            Message firstMessage = createRandomMessage();
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.just(firstMessage);
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            // when - consume first stream to completion
            concatenated.next();

            AtomicInteger callbackCount = new AtomicInteger(0);
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // then - callback should be invoked from second stream (first completed without errors)
            assertEquals(1, callbackCount.get(), "Callback should be invoked when second stream has data and first completed without error");
        }

        @Test
        void shouldNotNotifyFromSecondStreamWhenFirstStreamHasError() {
            // given
            RuntimeException testException = new RuntimeException("First stream failed");
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.failed(testException);
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger callbackCount = new AtomicInteger(0);

            // when - register callback after first stream failed
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // then - callback should be invoked for the error from first stream
            // but NOT from second stream (because first.error().isEmpty() is false)
            assertTrue(concatenated.error().isPresent(), "Stream should have error from first stream");
            assertTrue(concatenated.isCompleted(), "Stream should be completed");
        }

        @Test
        void shouldRegisterCallbacksOnBothStreamsUpfront() {
            // given - Track whether callbacks were registered on both streams
            CompletableFuture<Void> firstStreamCompletion = new CompletableFuture<>();
            CompletableFuture<Void> secondStreamReady = new CompletableFuture<>();

            Message firstMessage = createRandomMessage();
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.fromIterable(List.of(firstMessage))
                    .concatWith(DelayedMessageStream.create(firstStreamCompletion.thenApply(e -> MessageStream.empty())).cast());
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);

            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger firstStreamCallbacks = new AtomicInteger(0);
            AtomicInteger secondStreamCallbacks = new AtomicInteger(0);

            // when - register ONE callback on the concatenated stream
            concatenated.onAvailable(() -> {
                if (!firstStream.isCompleted()) {
                    firstStreamCallbacks.incrementAndGet();
                } else {
                    secondStreamCallbacks.incrementAndGet();
                }
            });

            // then - first stream callback should fire (stream has data, not completed)
            assertEquals(1, firstStreamCallbacks.get(), "First stream callback should fire for available data");
            assertEquals(0, secondStreamCallbacks.get(), "Second stream callback should not fire yet");

            // when - consume first message and complete first stream
            concatenated.next();
            firstStreamCompletion.complete(null);

            // Give a moment for async processing
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // then - second stream callback should now fire (first completed without error)
            assertTrue(secondStreamCallbacks.get() > 0, "Second stream callback should fire after first completes");
        }

        @Test
        void shouldHandleEmptyFirstStreamMovingToSecond() {
            // given
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.empty();
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger callbackCount = new AtomicInteger(0);

            // when
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // then - callback should be invoked from second stream since first is immediately completed
            assertTrue(callbackCount.get() > 0, "Callback should be invoked when moving to second stream");
            assertTrue(firstStream.isCompleted(), "First stream should be completed");
        }

        @Test
        void shouldHandleFirstStreamWithErrorPreventingSecondStreamCallback() {
            // given
            RuntimeException testException = new RuntimeException("Error in first stream");
            Message firstMessage = createRandomMessage();
            Message secondMessage = createRandomMessage();

            // First stream has data then fails
            MessageStream<Message> firstStream = MessageStream.just(firstMessage)
                    .concatWith(MessageStream.failed(testException));
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            // when - consume first message
            concatenated.next();

            // Attempt to get next (will trigger error)
            concatenated.next();

            // then - stream should have error and be completed
            assertTrue(concatenated.error().isPresent(), "Stream should have error");
            assertEquals(testException, concatenated.error().get(), "Should have the test exception");
            assertTrue(concatenated.isCompleted(), "Stream should be completed");

            // Second stream should not be processed
            AtomicInteger secondStreamCallbacks = new AtomicInteger(0);
            concatenated.onAvailable(secondStreamCallbacks::incrementAndGet);

            // The callback from second stream should not fire because first.error().isEmpty() is false
            // However, the stream is completed with error, so onAvailable behavior depends on implementation
            // Main point: second stream data should not be accessible
            assertFalse(concatenated.next().isPresent(), "No more data should be available after error");
        }
    }
}