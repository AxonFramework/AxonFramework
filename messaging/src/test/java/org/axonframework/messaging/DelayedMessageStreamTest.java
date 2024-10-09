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

import org.axonframework.messaging.MessageStream.MessageEntry;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DelayedMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class DelayedMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> testSubject(List<Message<String>> messages) {
        MessageStream<Message<String>> testStream = MessageStream.fromIterable(messages, SimpleMessageEntry::new);
        return DelayedMessageStream.create(CompletableFuture.completedFuture(testStream));
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages,
                                                      Exception failure) {
        return DelayedMessageStream.create(CompletableFuture.completedFuture(
                MessageStream.fromIterable(messages, SimpleMessageEntry::new)
                             .concatWith(MessageStream.failed(failure)))
        );
    }

    @Override
    Message<String> createRandomMessage() {
        return GenericMessage.asMessage("test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void createForExecutionExceptionReturnsFailedMessageStreamWithCause() {
        RuntimeException expected = new RuntimeException("oops");

        CompletableFuture<Object> result = DelayedMessageStream.create(CompletableFuture.failedFuture(expected))
                                                               .asCompletableFuture()
                                                               .thenApply(MessageEntry::message);

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
    }

    @Test
    void entryBecomeVisibleWhenFutureCompletes_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        CompletableFuture<MessageStream<Message<String>>> testFuture = new CompletableFuture<>();

        MessageStream<?> testSubject = DelayedMessageStream.create(testFuture)
                                                           .whenComplete(() -> invoked.set(true));

        CompletableFuture<?> result = testSubject.asCompletableFuture();
        assertFalse(result.isDone());
        assertFalse(invoked.get());

        testFuture.complete(MessageStream.just(createRandomMessage()));

        result = testSubject.asCompletableFuture();
        assertTrue(result.isDone());
        assertTrue(invoked.get());
    }

    @Test
    void entryBecomeVisibleWhenFutureCompletes_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Message<String> expected = createRandomMessage();
        CompletableFuture<MessageStream<Message<String>>> testFuture = new CompletableFuture<>();

        MessageStream<Message<String>> testSubject = DelayedMessageStream.create(testFuture)
                                                                         .whenComplete(() -> invoked.set(true));

        StepVerifier.create(testSubject.asFlux())
                    .verifyTimeout(Duration.ofMillis(250));
        // Verify timeout cancels the Flux, so we need to create the test subject again after this.
        assertFalse(invoked.get());

        testFuture.complete(MessageStream.just(expected));
        testSubject = DelayedMessageStream.create(testFuture)
                                          .whenComplete(() -> invoked.set(true));

        StepVerifier.create(testSubject.asFlux())
                    .expectNextMatches(entry -> entry.message().equals(expected))
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void reduceResultBecomesVisibleWhenFutureCompletes() {
        Message<String> randomMessage = createRandomMessage();
        String expected = randomMessage.getPayload() + randomMessage.getPayload();
        MessageStream<Message<String>> futureStream = testSubject(List.of(randomMessage, randomMessage));
        CompletableFuture<MessageStream<Message<String>>> testFuture = new CompletableFuture<>();

        MessageStream<Message<String>> testSubject = DelayedMessageStream.create(testFuture);

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> entry.message().getPayload() + entry.message().getPayload()
        );
        assertFalse(result.isDone());

        testFuture.complete(futureStream);

        result = testSubject.reduce("", (base, entry) -> entry.message().getPayload() + entry.message().getPayload());
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }
}