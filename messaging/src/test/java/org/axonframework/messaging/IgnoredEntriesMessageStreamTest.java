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

import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link IgnoredEntriesMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class IgnoredEntriesMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> completedTestSubject(List<Message<String>> messages) {
        Assumptions.assumeTrue(messages.isEmpty(), "EmptyMessageStream ignores entries");
        return MessageStream.fromIterable(messages).ignoreEntries();
    }

    @Override
    MessageStream.Single<Message<String>> completedSingleStreamTestSubject(Message<String> message) {
        Assumptions.abort("IgnoredEntriesMessageStream ignores entries");
        return MessageStream.just(message).ignoreEntries();
    }

    @Override
    MessageStream.Empty<Message<String>> completedEmptyStreamTestSubject() {
        return MessageStream.empty().ignoreEntries().cast();
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages, Exception failure) {
        Assumptions.abort("IgnoredEntriesMessageStream ignores entries");
        return MessageStream.fromIterable(messages).concatWith(MessageStream.failed(failure)).ignoreEntries();
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void shouldProcessMessagesButIgnoresEntries() {
        var processed = new AtomicBoolean(false);
        var messages = List.of(createRandomMessage());

        var result = MessageStream.fromIterable(messages)
                                  .ignoreEntries()
                                  .onNext(e -> processed.set(true))
                                  .asCompletableFuture();
        assertTrue(result.isDone());
        assertNull(result.join());
        assertFalse(processed.get());
    }

    @Test
    void shouldEmitOriginalExceptionAsFailure() {
        var testSubject = MessageStream.failed(new MockException()).ignoreEntries();

        var actual = testSubject.first().asCompletableFuture();

        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }
}