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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnNextItemMessageStreamTest extends MessageStreamTest<String> {

    @SuppressWarnings("unchecked")
    @Test
    void onNextNotInvokedOnEmptyStream() {
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

    @Override
    OnNextItemMessageStream<Message<String>> createTestSubject(List<Message<String>> values) {
        return new OnNextItemMessageStream<>(MessageStream.fromIterable(values), m -> {
        });
    }

    @Override
    OnNextItemMessageStream<Message<String>> createTestSubject(List<Message<String>> values, Exception failure) {
        return new OnNextItemMessageStream<>(MessageStream.fromIterable(values).concatWith(MessageStream.failed(failure)),
                                         m -> {
                                         });
    }

    @Override
    String createRandomValidStreamEntry() {
        return "RandomValue" + ThreadLocalRandom.current().nextInt(10000);
    }
}