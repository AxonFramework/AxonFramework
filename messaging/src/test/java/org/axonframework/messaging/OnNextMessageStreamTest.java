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

import jakarta.validation.constraints.NotNull;
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
class OnNextMessageStreamTest extends MessageStreamTest<String> {

    private static final @NotNull Consumer<Message<String>> NO_OP_ON_NEXT = message -> {
    };

    @Override
    MessageStream<Message<String>> testSubject(List<Message<String>> messages) {
        return new OnNextMessageStream<>(MessageStream.fromIterable(messages), NO_OP_ON_NEXT);
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages, Exception failure) {
        return new OnNextMessageStream<>(MessageStream.fromIterable(messages)
                                                      .concatWith(MessageStream.failed(failure)),
                                         NO_OP_ON_NEXT);
    }

    @Override
    String createRandomValidEntry() {
        return "test-" + ThreadLocalRandom.current().nextInt(10000);
    }

    @Test
    void onNextNotInvokedOnEmptyStream() {
        //noinspection unchecked
        Consumer<Message<?>> handler = mock();
        MessageStream<Message<?>> testSubject = MessageStream.empty().onNextItem(handler);

        testSubject.asCompletableFuture().isDone();
        verify(handler, never()).accept(any());
    }

    @Test
    void verifyOnNextInvokedForFirstElementWhenUsingOnCompletableFuture() {
        List<Message<?>> seen = new ArrayList<>();
        List<Message<?>> items = List.of(GenericMessage.asMessage("first"), GenericMessage.asMessage("second"));
        CompletableFuture<Message<?>> actual = MessageStream.fromIterable(items)
                                                            .onNextItem(seen::add)
                                                            .asCompletableFuture();

        assertTrue(actual.isDone());
        assertEquals(1, seen.size());
        assertEquals("first", seen.getFirst().getPayload());
    }

    @Test
    void verifyOnNextInvokedForAllElementsWhenUsingAsFlux() {
        List<Message<?>> seen = new ArrayList<>();
        List<Message<?>> items = List.of(GenericMessage.asMessage("first"), GenericMessage.asMessage("second"));
        StepVerifier.create(MessageStream.fromIterable(items)
                                         .onNextItem(seen::add)
                                         .asFlux())
                    .expectNextCount(2)
                    .verifyComplete();

        assertEquals(2, seen.size());
        assertEquals("first", seen.getFirst().getPayload());
        assertEquals("second", seen.get(1).getPayload());
    }
}