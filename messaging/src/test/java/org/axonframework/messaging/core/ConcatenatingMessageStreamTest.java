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

package org.axonframework.messaging.core;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ConcatenatingMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Simon Zambrovski
 */
class ConcatenatingMessageStreamTest extends MessageStreamTest<Message> {

    @Override
    protected MessageStream<Message> completedTestSubject(List<Message> messages) {
        if (messages.isEmpty()) {
            return new ConcatenatingMessageStream<>(MessageStream.empty().cast(), MessageStream.empty().cast());
        } else if (messages.size() == 1) {
            return new ConcatenatingMessageStream<>(MessageStream.just(messages.getFirst()),
                                                    MessageStream.empty().cast());
        }
        return new ConcatenatingMessageStream<>(
                MessageStream.just(messages.getFirst()),
                MessageStream.fromIterable(messages.subList(1, messages.size()))
        );
    }

    @Override
    protected MessageStream.Single<Message> completedSingleStreamTestSubject(Message message) {
        Assumptions.abort("ConcatenatingMessageStream doesn't support explicit single-value streams");
        return null;
    }

    @Override
    protected MessageStream.Empty<Message> completedEmptyStreamTestSubject() {
        Assumptions.abort("ConcatenatingMessageStream doesn't support explicitly empty streams");
        return null;
    }

    @Override
    protected MessageStream<Message> failingTestSubject(List<Message> messages,
                                                        RuntimeException failure) {
        return completedTestSubject(messages).concatWith(MessageStream.failed(failure));
    }

    @Override
    protected Message createRandomMessage() {
        return new GenericMessage(new MessageType("message"),
                                  "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Nested
    class ConcatenatingOnAvailableFromFutureTest {

        private final AtomicBoolean callbackCalled = new AtomicBoolean();

        private CompletableFuture<Message> future1;
        private CompletableFuture<Message> future2;

        @BeforeEach
        public void reset() {
            future1 = new CompletableFuture<>();
            future2 = new CompletableFuture<>();
            MessageStream<Message> stream1 = MessageStream.fromFuture(future1);
            MessageStream<Message> stream2 = MessageStream.fromFuture(future2);
            MessageStream<Message> testSubject = stream1.concatWith(stream2);
            testSubject.setCallback(() -> callbackCalled.set(true));
            assertFalse(callbackCalled.getAndSet(false));
        }

        @Test
        void shouldNotDoCallbackIfFirstIsNotCompleted() {
            future2.complete(createRandomMessage());
            assertFalse(callbackCalled.getAndSet(false));
        }

        @Test
        void shouldDoCallbackIfFirstIsCompletedWithMessage() {
            future1.complete(createRandomMessage());
            assertTrue(callbackCalled.getAndSet(false));
        }

        @Test
        void shouldDoCallbackIfFirstIsCompletedWithNull() {
            future1.complete(null);
            assertFalse(callbackCalled.getAndSet(false));
        }

        @Test
        void shouldDoCallbackIfFirstIsCompletedWithException() {
            future1.completeExceptionally(new IllegalArgumentException("Error"));
            assertTrue(callbackCalled.getAndSet(false));
        }

        @Test
        void shouldDoCallbackAfterFirstStreamCompletedWithoutMessages() {
            future1 = CompletableFuture.completedFuture(null);
            future2 = new CompletableFuture<>();
            MessageStream<Message> stream1 = MessageStream.fromFuture(future1);
            MessageStream<Message> stream2 = MessageStream.fromFuture(future2);
            MessageStream<Message> testSubject = stream1.concatWith(stream2);
            testSubject.setCallback(() -> callbackCalled.set(true));

            assertTrue(stream1.isCompleted());
            assertFalse(stream1.hasNextAvailable());

            assertFalse(callbackCalled.getAndSet(false));

            // Only after stream 2 has messages should the callback be called
            future2.complete(createRandomMessage());

            assertTrue(callbackCalled.getAndSet(false));
        }
    }

    @Nested
    class ConcatenatingOnAvailableOnStreamsTest {

        private final QueueMessageStream<?> stream1 = new QueueMessageStream<>();
        private final QueueMessageStream<?> stream2 = Mockito.spy(new QueueMessageStream<>());
        private final MessageStream<?> testSubject = stream1.concatWith(stream2.cast());

        private final AtomicBoolean callbackCalled = new AtomicBoolean();

        @BeforeEach
        void reset() {
            testSubject.setCallback(() -> callbackCalled.set(true));
        }

        @Test
        void shouldDoCallbackIfFirstIsCompletedAfterSecond() {
            stream2.complete();
            assertFalse(callbackCalled.getAndSet(false));
            stream1.complete();
            assertTrue(callbackCalled.getAndSet(false));
        }

        @Test
        void shouldCloseSecondIfFirstCompletesWithError() {
            stream1.completeExceptionally(new IllegalArgumentException("Error"));
            assertTrue(callbackCalled.getAndSet(false));
            assertTrue(testSubject.error().isPresent());
            verify(stream2).close();
        }

        @Test
        void shouldDoCallbackWhenFirstStreamHasMessageAndSecondStreamHasMessage() {
            var future1 = new CompletableFuture<Message>();
            var future2 = new CompletableFuture<Message>();
            var stream = MessageStream.fromFuture(future1).concatWith(MessageStream.fromFuture(future2));

            stream.setCallback(() -> callbackCalled.set(true));

            future1.complete(createRandomMessage());

            assertTrue(callbackCalled.getAndSet(false));
            assertTrue(stream.next().isPresent());

            future2.complete(createRandomMessage());

            assertTrue(callbackCalled.getAndSet(false));
            assertTrue(stream.next().isPresent());
        }

        @Test
        void shouldDoCallbackWhenFirstStreamCompletesExceptionally() {
            var future = new CompletableFuture<Message>();
            var stream = MessageStream.fromFuture(future).concatWith(MessageStream.empty());
            stream.setCallback(() -> callbackCalled.set(true));

            future.completeExceptionally(new IllegalArgumentException("Error"));

            assertTrue(callbackCalled.getAndSet(false));
        }
    }
}