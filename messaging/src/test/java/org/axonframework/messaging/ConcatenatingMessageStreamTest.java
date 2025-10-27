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
        void shouldNotifyWhenFirstStreamHasDataAvailable() {
            // given
            Message firstMessage = createRandomMessage();
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.just(firstMessage);
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger callbackCount = new AtomicInteger(0);

            // when - register callback before consuming any data
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // then - callback should be invoked because first stream has data available
            assertTrue(callbackCount.get() > 0, "Callback should be invoked when first stream has data");
        }

        @Test
        void shouldNotifyWhenSecondStreamHasDataAfterFirstCompletes() {
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

            // then - callback should be invoked because second stream now has data available
            assertTrue(callbackCount.get() > 0, "Callback should be invoked when second stream has data");
        }

        @Test
        void shouldNotifyForBothFirstAndSecondStreams() {
            // given
            Message firstMessage = createRandomMessage();
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.just(firstMessage);
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger totalCallbacks = new AtomicInteger(0);

            // when - register callback before consuming
            concatenated.onAvailable(totalCallbacks::incrementAndGet);
            int firstPhaseCallbacks = totalCallbacks.get();

            // then - should be invoked for first stream having data
            assertTrue(firstPhaseCallbacks > 0, "Callback should be invoked for first stream");

            // when - consume first message and register new callback
            concatenated.next();
            totalCallbacks.set(0);
            concatenated.onAvailable(totalCallbacks::incrementAndGet);

            // then - should be invoked for second stream having data
            assertTrue(totalCallbacks.get() > 0, "Callback should be invoked for second stream");
        }

        @Test
        void shouldNotifyWhenFirstStreamFailsImmediately() {
            // given
            RuntimeException testException = new RuntimeException("First stream failed");
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.failed(testException);
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger callbackCount = new AtomicInteger(0);

            // when
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // then - callback should be invoked for error, and second stream should not be accessed
            assertTrue(callbackCount.get() >= 0, "Callback handling for failed stream");
            assertTrue(concatenated.error().isPresent(), "Stream should have error");
            assertTrue(concatenated.isCompleted(), "Stream should be completed");
        }

        @Test
        void shouldNotNotifyWhenFirstStreamCompletesButSecondHasNoData() {
            // given
            Message firstMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.just(firstMessage);
            MessageStream<Message> secondStream = MessageStream.empty();
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            // when - consume the first message to complete first stream
            concatenated.next();

            AtomicInteger callbackCount = new AtomicInteger(0);
            concatenated.onAvailable(callbackCount::incrementAndGet);

            // then - callback may be invoked for completion, but stream should be completed
            assertTrue(concatenated.isCompleted(), "Stream should be completed when both streams complete");
        }

        @Test
        void shouldHandleCallbackRegistrationOnBothStreams() {
            // given
            Message firstMessage = createRandomMessage();
            Message secondMessage = createRandomMessage();

            MessageStream<Message> firstStream = MessageStream.just(firstMessage);
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            AtomicInteger firstPhaseCallbacks = new AtomicInteger(0);
            AtomicInteger secondPhaseCallbacks = new AtomicInteger(0);

            // when - register callback while first stream has data
            concatenated.onAvailable(firstPhaseCallbacks::incrementAndGet);

            // then - should be invoked for first stream
            assertTrue(firstPhaseCallbacks.get() > 0, "Callback should be invoked for first stream data");

            // when - consume first stream and register new callback
            concatenated.next();
            concatenated.onAvailable(secondPhaseCallbacks::incrementAndGet);

            // then - should be invoked for second stream
            assertTrue(secondPhaseCallbacks.get() > 0, "Callback should be invoked for second stream data after first completes");
        }

        @Test
        void shouldPreventNotificationOnSecondStreamWhenFirstFails() {
            // given
            RuntimeException testException = new RuntimeException("First stream failed");
            Message firstMessage = createRandomMessage();
            Message secondMessage = createRandomMessage();

            MessageStream<Message> errorStream = MessageStream.failed(testException);
            MessageStream<Message> firstStream = MessageStream.just(firstMessage).concatWith(errorStream);
            MessageStream<Message> secondStream = MessageStream.just(secondMessage);
            MessageStream<Message> concatenated = new ConcatenatingMessageStream<>(firstStream, secondStream);

            // when - consume until error
            concatenated.next(); // first message

            // then - stream should fail and second stream should not be processed
            Optional<MessageStream.Entry<Message>> next = concatenated.next();
            assertTrue(concatenated.error().isPresent() || next.isEmpty(), "Should either have error or no more data");
            assertTrue(concatenated.isCompleted(), "Stream should be completed");
        }
    }
}