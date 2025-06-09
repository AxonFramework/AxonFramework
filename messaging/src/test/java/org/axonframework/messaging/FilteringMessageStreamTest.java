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

import org.axonframework.messaging.MessageStream.Entry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link FilteringMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Steven van Beelen
 */
class FilteringMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> completedTestSubject(List<Message<String>> messages) {
        return new FilteringMessageStream<>(MessageStream.fromIterable(messages), entry -> true);
    }

    @Override
    MessageStream.Single<Message<String>> completedSingleStreamTestSubject(Message<String> message) {
        return new FilteringMessageStream.Single<>(MessageStream.just(message), entry -> true);
    }

    @Override
    MessageStream.Empty<Message<String>> completedEmptyStreamTestSubject() {
        Assumptions.abort("ConcatenatingMessageStream doesn't support explicitly empty streams");
        return null;
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages, Exception failure) {
        return completedTestSubject(messages).concatWith(MessageStream.failed(failure));
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void peekAdvancesToFirstMatchingEntry() {
        //given
        Message<String> first = new GenericMessage<>(new MessageType("type"), "skip");
        Message<String> second = new GenericMessage<>(new MessageType("type"), "keep");
        MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second));
        FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate,
                                                                                      entry -> entry.message()
                                                                                                    .getPayload()
                                                                                                    .equals("keep"));

        //when
        Optional<Entry<Message<String>>> peeked = stream.peek();
        Optional<Entry<Message<String>>> next = stream.next();
        Optional<Entry<Message<String>>> after = stream.next();

        //then
        assertTrue(peeked.isPresent());
        assertEquals("keep", peeked.get().message().getPayload());
        assertTrue(next.isPresent());
        assertEquals("keep", next.get().message().getPayload());
        assertFalse(after.isPresent());
    }

    @Test
    void nextSkipsFilteredOutEntriesAndReturnsOnlyMatching() {
        //given
        Message<String> first = new GenericMessage<>(new MessageType("type"), "skip1");
        Message<String> second = new GenericMessage<>(new MessageType("type"), "keep1");
        Message<String> third = new GenericMessage<>(new MessageType("type"), "skip2");
        Message<String> fourth = new GenericMessage<>(new MessageType("type"), "keep2");
        MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second, third, fourth));
        FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate,
                                                                                      entry -> entry.message()
                                                                                                    .getPayload()
                                                                                                    .startsWith("keep"));

        //when & then
        Optional<Entry<Message<String>>> firstMatch = stream.next();
        assertTrue(firstMatch.isPresent());
        assertEquals("keep1", firstMatch.get().message().getPayload());

        Optional<Entry<Message<String>>> secondMatch = stream.next();
        assertTrue(secondMatch.isPresent());
        assertEquals("keep2", secondMatch.get().message().getPayload());

        Optional<Entry<Message<String>>> after = stream.next();
        assertFalse(after.isPresent());
    }

    @Test
    void returnsEmptyForStreamWithOnlyNonMatchingElements() {
        //given
        Message<String> first = new GenericMessage<>(new MessageType("type"), "skip1");
        Message<String> second = new GenericMessage<>(new MessageType("type"), "skip2");
        MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second));
        FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate,
                                                                                      entry -> entry.message()
                                                                                                    .getPayload()
                                                                                                    .startsWith("keep"));

        //when & then
        assertFalse(stream.hasNextAvailable());
        assertFalse(stream.peek().isPresent());
        assertFalse(stream.next().isPresent());
    }
}