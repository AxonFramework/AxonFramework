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

import org.axonframework.messaging.core.MessageStream.Entry;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link OnNextMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class OnNextMessageStreamTest extends MessageStreamTest<Message> {

    private static final Consumer<Entry<Message>> NO_OP_ON_NEXT = message -> {
    };

    @Override
    protected MessageStream<Message> completedTestSubject(List<Message> messages) {
        return new OnNextMessageStream<>(MessageStream.fromIterable(messages), NO_OP_ON_NEXT);
    }

    @Override
    protected MessageStream.Single<Message> completedSingleStreamTestSubject(Message message) {
        return new OnNextMessageStream.Single<>(MessageStream.just(message), NO_OP_ON_NEXT);
    }

    @Override
    protected MessageStream.Empty<Message> completedEmptyStreamTestSubject() {
        Assumptions.abort("OnNextMessageStream does not support empty streams");
        return null;
    }

    @Override
    protected MessageStream<Message> failingTestSubject(List<Message> messages,
                                                        RuntimeException failure) {
        return new OnNextMessageStream<>(MessageStream.fromIterable(messages)
                                                      .concatWith(MessageStream.failed(failure)),
                                         NO_OP_ON_NEXT);
    }

    @Override
    protected Message createRandomMessage() {
        return new GenericMessage(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void onNextNotInvokedOnEmptyStream() {
        //noinspection unchecked
        Consumer<Entry<Message>> handler = mock();
        MessageStream<Message> testSubject = MessageStream.empty().onNext(handler);

        testSubject.first().asCompletableFuture().isDone();
        verify(handler, never()).accept(any());
    }

    @Test
    void verifyOnNextInvokedForFirstElementWhenUsingOnCompletableFuture() {
        List<Entry<Message>> seen = new ArrayList<>();
        Message first = createRandomMessage();
        List<Message> messages = List.of(first, createRandomMessage());

        CompletableFuture<Message> actual = MessageStream.fromIterable(messages)
                                                                 .onNext(seen::add)
                                                                 .first()
                                                                 .asCompletableFuture()
                                                                 .thenApply(Entry::message);

        assertTrue(actual.isDone());
        assertEquals(1, seen.size());
        assertEquals(first, seen.getFirst().message());
    }

    @Test
    void verifyOnNextInvokedForAllElementsWhenUsingAsFlux() {
        List<Entry<Message>> seen = new ArrayList<>();
        Message first = createRandomMessage();
        Message second = createRandomMessage();
        List<Message> messages = List.of(first, second);

        StepVerifier.create(FluxUtils.of(MessageStream.fromIterable(messages).onNext(seen::add)))
            .expectNextCount(2)
            .verifyComplete();

        assertEquals(2, seen.size());
        assertEquals(first, seen.getFirst().message());
        assertEquals(second, seen.get(1).message());
    }
}