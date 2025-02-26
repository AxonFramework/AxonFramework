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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link IgnoredEntriesMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class IgnoredEntriesMessageStreamTest extends MessageStreamTest<Message<Void>> {

    @Override
    MessageStream<Message<Void>> completedTestSubject(List<Message<Void>> messages) {
        Assumptions.abort("IgnoredEntriesMessageStream doesn't support content");
        return MessageStream.fromIterable(messages).ignoreEntries();
    }

    @Override
    MessageStream.Single<Message<Void>> completedSingleStreamTestSubject(Message<Void> message) {
        Assumptions.abort("IgnoredEntriesMessageStream doesn't support content");
        return MessageStream.just(message).ignoreEntries();
    }

    @Override
    MessageStream.Empty<Message<Void>> completedEmptyStreamTestSubject() {
        return MessageStream.empty().ignoreEntries();
    }

    @Override
    MessageStream<Message<Void>> failingTestSubject(List<Message<Void>> messages, Exception failure) {
        return MessageStream.fromIterable(messages)
                            .concatWith(MessageStream.failed(failure))
                            .ignoreEntries();
    }

    @Override
    Message<Void> createRandomMessage() {
//        Assumptions.abort("IgnoredEntriesMessageStream doesn't support content");
        return new GenericMessage<>(new MessageType("message"),
                                    null);
    }

    @Test
    void shouldReturnFailedMessageStreamOnFailingCompletionHandler() {
        var expected = new RuntimeException("oops");

        var result = MessageStream.empty()
                                  .ignoreEntries()
                                  .whenComplete(() -> {
                                      throw expected;
                                  })
                                  .first()
                                  .asCompletableFuture()
                                  .thenApply(MessageStream.Entry::message);

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
    }

//    @Test
//    void shouldProcessMessagesButIgnoresEntries() {
//        var processed = new AtomicBoolean(false);
//        var messages = List.of(createRandomMessage());
//
//        var result = MessageStream.fromIterable(messages)
//                                  .onNext(e -> {
//                                      processed.set(true);
//                                  })
//                                  .ignoreEntries()
//                                  .asCompletableFuture();
//        assertTrue(result.isDone());
//        assertNull(result.join());
//        assertFalse(processed.get());
//    }
}