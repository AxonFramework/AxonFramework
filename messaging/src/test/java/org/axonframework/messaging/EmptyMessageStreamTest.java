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

import org.axonframework.messaging.MessageStream.Entry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EmptyMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class EmptyMessageStreamTest extends MessageStreamTest<Message<Void>> {

    @Override
    MessageStream<Message<Void>> completedTestSubject(List<Message<Void>> messages) {
        Assumptions.assumeTrue(messages.isEmpty(), "EmptyMessageStream doesn't support content");
        return MessageStream.empty();
    }

    @Override
    MessageStream<Message<Void>> failingTestSubject(List<Message<Void>> messages,
                                                    Exception failure) {
        Assumptions.abort("EmptyMessageStream doesn't support failed streams");
        return MessageStream.empty();
    }

    @Override
    Message<Void> createRandomMessage() {
        Assumptions.abort("EmptyMessageStream doesn't support content");
        return null;
    }

    @Test
    void doesNothingOnErrorContinue() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        CompletableFuture<Entry<Message<?>>> result = MessageStream.empty()
                                                                   .onErrorContinue(e -> {
                                                                       invoked.set(true);
                                                                       return MessageStream.empty();
                                                                   })
                                                                   .firstAsCompletableFuture();
        assertTrue(result.isDone());
        assertNull(result.join());
        assertFalse(invoked.get());
    }

    @Test
    void shouldReturnFailedMessageStreamOnFailingCompletionHandler() {
        RuntimeException expected = new RuntimeException("oops");

        CompletableFuture<Object> result = MessageStream.empty()
                                                        .whenComplete(() -> {
                                                            throw expected;
                                                        })
                                                        .firstAsCompletableFuture()
                                                        .thenApply(Entry::message);

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
    }
}