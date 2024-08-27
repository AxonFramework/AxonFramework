/*
 * Copyright (c) 2010-2024. Axon Framework
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
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.messaging.GenericMessage.asMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DelayedMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class DelayedMessageStreamTest extends MessageStreamTest<String> {

    @Override
    MessageStream<Message<String>> testSubject(List<Message<String>> messages) {
        return DelayedMessageStream.create(CompletableFuture.completedFuture(MessageStream.fromIterable(messages)));
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages, Exception failure) {
        return DelayedMessageStream.create(CompletableFuture.completedFuture(
                MessageStream.fromIterable(messages)
                             .concatWith(MessageStream.failed(failure)))
        );
    }

    @Override
    String createRandomValidEntry() {
        return "test--" + ThreadLocalRandom.current().nextInt(10000);
    }

    @Test
    void createForExecutionExceptionReturnsFailedMessageStreamWithCause() {
        RuntimeException expected = new RuntimeException("oops");

        CompletableFuture<Message<?>> result = DelayedMessageStream.create(CompletableFuture.failedFuture(expected))
                                                                   .asCompletableFuture();

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
    }

    @Test
    void messageBecomeVisibleWhenFutureCompletes_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        CompletableFuture<MessageStream<Message<?>>> testFuture = new CompletableFuture<>();

        MessageStream<Message<?>> testSubject = DelayedMessageStream.create(testFuture)
                                                                    .whenComplete(() -> invoked.set(true));

        CompletableFuture<Message<?>> result = testSubject.asCompletableFuture();
        assertFalse(result.isDone());
        assertFalse(invoked.get());

        testFuture.complete(MessageStream.just(asMessage(createRandomValidEntry())));

        result = testSubject.asCompletableFuture();
        assertTrue(result.isDone());
        assertTrue(invoked.get());
    }

    @Test
    void messageBecomeVisibleWhenFutureCompletes_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Message<Object> expected = asMessage(createRandomValidEntry());
        CompletableFuture<MessageStream<Message<?>>> testFuture = new CompletableFuture<>();

        MessageStream<Message<?>> testSubject = DelayedMessageStream.create(testFuture)
                                                                    .whenComplete(() -> invoked.set(true));

        StepVerifier.create(testSubject.asFlux())
                    .expectNoEvent(Duration.ofMillis(250));
        assertFalse(invoked.get());

        testFuture.complete(MessageStream.just(expected));

        StepVerifier.create(testSubject.asFlux())
                    .expectNext(expected)
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void reduceResultBecomesVisibleWhenFutureCompletes() {
        String randomPayload = createRandomValidEntry();
        String expected = randomPayload + randomPayload;
        MessageStream<Message<String>> futureStream = testSubject(List.of(asMessage(randomPayload),
                                                                          asMessage(randomPayload)));
        CompletableFuture<MessageStream<Message<String>>> testFuture = new CompletableFuture<>();

        MessageStream<Message<String>> testSubject = DelayedMessageStream.create(testFuture);

        CompletableFuture<String> result =
                testSubject.reduce("", (base, message) -> message.getPayload() + message.getPayload());
        assertFalse(result.isDone());

        testFuture.complete(futureStream);

        result = testSubject.reduce("", (base, message) -> message.getPayload() + message.getPayload());
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }
}