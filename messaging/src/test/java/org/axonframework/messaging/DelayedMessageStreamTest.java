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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DelayedMessageStreamTest {

    private CompletableFuture<MessageStream<Message<?>>> future;

    private MessageStream<Message<?>> testSubject;

    @BeforeEach
    void setUp() {
        future = new CompletableFuture<>();

        testSubject = DelayedMessageStream.create(future);
    }

    @Test
    @Disabled("TODO #3063 - Clean-up MessageStream API")
    void elementsBecomeVisibleWhenFutureCompletes() {
        AtomicBoolean completed = new AtomicBoolean(false);

        testSubject.whenComplete(() -> completed.set(true));

        CompletableFuture<Message<?>> actual = testSubject.asCompletableFuture();
        assertFalse(actual.isDone());
        assertFalse(completed.get());

        future.complete(MessageStream.just(GenericMessage.asMessage("OK")));

        assertTrue(actual.isDone());
        assertTrue(completed.get());
    }
}