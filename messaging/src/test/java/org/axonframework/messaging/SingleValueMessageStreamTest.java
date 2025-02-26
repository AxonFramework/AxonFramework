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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SingleValueMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class SingleValueMessageStreamTest extends MessageStreamTest<Message<String>, Message<String>> {

    @Override
    MessageStream<Message<String>> completedTestSubject(List<Message<String>> messages) {
        Assumptions.assumeTrue(messages.size() == 1, "SingleValueMessageStream only supports a single value");
        return MessageStream.just(messages.getFirst());
    }

    @Override
    MessageStream.Single<Message<String>> completedSingleStreamTestSubject(Message<String> message) {
        return MessageStream.just(message);
    }

    @Override
    MessageStream.Empty<Message<String>> completedEmptyStreamTestSubject() {
        Assumptions.abort("DelayedMessageStream does not support empty streams");
        return null;
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages,
                                                      Exception failure) {
        Assumptions.assumeTrue(messages.isEmpty(),
                               "SingleValueMessageStream only supports failures without regular values");
        return MessageStream.fromFuture(CompletableFuture.failedFuture(failure));
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void shouldReturnNextItemOnceWhenFutureCompletes() {
        CompletableFuture<MessageStream.Entry<Message<?>>> future = new CompletableFuture<>();
        SingleValueMessageStream<Message<?>> testSubject = new SingleValueMessageStream<>(future);

        assertFalse(testSubject.hasNextAvailable());
        assertFalse(testSubject.next().isPresent());

        future.complete(new SimpleEntry<>(createRandomMessage()));

        assertTrue(testSubject.hasNextAvailable());
        assertTrue(testSubject.next().isPresent());
        assertFalse(testSubject.hasNextAvailable());
        assertFalse(testSubject.next().isPresent());
    }

    @Test
    void shouldInvokeOnAvailableWhenFutureCompletes() {
        CompletableFuture<MessageStream.Entry<Message<?>>> future = new CompletableFuture<>();
        SingleValueMessageStream<Message<?>> testSubject = new SingleValueMessageStream<>(future);

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
        future.complete(new SimpleEntry<>(createRandomMessage()));

        assertTrue(invoked.get());
    }

    @Test
    void closeCancelsTheCompletableFuture() {
        CompletableFuture<MessageStream.Entry<Message<?>>> future = new CompletableFuture<>();
        SingleValueMessageStream<Message<?>> testSubject = new SingleValueMessageStream<>(future);

        testSubject.close();

        assertTrue(future.isCancelled());
    }
}