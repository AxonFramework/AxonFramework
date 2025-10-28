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
    MessageStream<Message> completedTestSubject(List<Message> messages) {
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
    class ConcatenatingOnAvailableFromFutureTest {

        private final AtomicBoolean onAvailable = new AtomicBoolean();

        private CompletableFuture<Message> future1;
        private CompletableFuture<Message> future2;

        @BeforeEach
        public void reset() {
            future1 = new CompletableFuture<>();
            future2 = new CompletableFuture<>();
            MessageStream<Message> stream1 = MessageStream.fromFuture(future1);
            MessageStream<Message> stream2 = MessageStream.fromFuture(future2);
            MessageStream<Message> testSubject = stream1.concatWith(stream2);
            testSubject.setCallback(() -> onAvailable.set(true));
            assertFalse(onAvailable.getAndSet(false));
        }

        @Test
        void shouldNotCallOnAvailableIfFirstIsNotCompleted() {
            future2.complete(createRandomMessage());
            assertFalse(onAvailable.getAndSet(false));
        }

        @Test
        void shouldCallOnAvailableIfFirstIsCompletedWithMessage() {

            future1.complete(createRandomMessage());
            assertTrue(onAvailable.getAndSet(false));
        }

        @Test
        void shouldCallOnAvailableIfFirstIsCompletedWithNull() {
            future1.complete(null);
            assertFalse(onAvailable.getAndSet(false));
        }

        @Test
        void shouldCallOnAvailableIfFirstIsCompletedWithException() {

            future1.completeExceptionally(new IllegalArgumentException("Error"));
            assertTrue(onAvailable.getAndSet(false));
        }

        @Test
        void bla() {
            future1 = CompletableFuture.completedFuture(null);
            future2 = new CompletableFuture<>();
            MessageStream<Message> stream1 = MessageStream.fromFuture(future1);
            MessageStream<Message> stream2 = MessageStream.fromFuture(future2);
            MessageStream<Message> testSubject = stream1.concatWith(stream2);
            testSubject.setCallback(() -> onAvailable.set(true));


            assertTrue(stream1.isCompleted());
            assertFalse(stream1.hasNextAvailable());

            assertFalse(onAvailable.getAndSet(false));

            // action
            future2.complete(createRandomMessage());

            assertTrue(onAvailable.getAndSet(false));
        }
    }

    @Nested
    class ConcatenatingOnAvailableOnStreamsTest {

        private final QueueMessageStream<?> stream1 = new QueueMessageStream<>();
        private final QueueMessageStream<?> stream2 = Mockito.spy(new QueueMessageStream<>());
        private final MessageStream<?> testSubject = stream1.concatWith(stream2.cast());

        private final AtomicBoolean onAvailable = new AtomicBoolean();


        @BeforeEach
        void reset() {
            testSubject.setCallback(() -> onAvailable.set(true));
        }

        @Test
        void shouldInvokeSetCallbackIfFirstIsCompletedAfterSecond() {
            stream2.complete();
            assertFalse(onAvailable.getAndSet(false));
            stream1.complete();
            assertTrue(onAvailable.getAndSet(false));
        }

        @Test
        void shouldCloseSecondIfFirstCompletesWithError() {

            stream1.completeExceptionally(new IllegalArgumentException("Error"));
            assertTrue(onAvailable.getAndSet(false));
            assertTrue(testSubject.error().isPresent());
            verify(stream2).close();
        }

        @Test
        void shouldInvokeSetHandler() {
            var future = new CompletableFuture<Message>();
            var future2 = new CompletableFuture<Message>();
            var stream1 = MessageStream.fromFuture(future);
            var stream = stream1.concatWith(MessageStream.fromFuture(future2));

            stream.setCallback(() -> onAvailable.set(true));

            future.complete(createRandomMessage());

            assertTrue(onAvailable.getAndSet(false));
            var message1 = stream.next();
            assertTrue(message1.isPresent());

            future2.complete(createRandomMessage());
            assertTrue(onAvailable.getAndSet(false));
            var message2 = stream.next();
            assertTrue(message2.isPresent());
        }

        @Test
        void shouldInvokeSetHandlerWith() {
            var future = new CompletableFuture<Message>();
            var stream = MessageStream.fromFuture(future).concatWith(MessageStream.empty());

            stream.setCallback(() -> onAvailable.set(true));

            future.completeExceptionally(new IllegalArgumentException("Error"));
            assertTrue(onAvailable.getAndSet(false));
        }
    }
}