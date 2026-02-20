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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link FilteringMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Steven van Beelen
 */
class FilteringMessageStreamTest extends MessageStreamTest<Message> {

    @Override
    protected MessageStream<Message> completedTestSubject(List<Message> messages) {
        return new FilteringMessageStream<>(MessageStream.fromIterable(messages), entry -> true);
    }

    @Override
    protected MessageStream.Single<Message> completedSingleStreamTestSubject(Message message) {
        return new FilteringMessageStream.Single<>(MessageStream.just(message), entry -> true);
    }

    @Override
    protected MessageStream.Empty<Message> completedEmptyStreamTestSubject() {
        Assumptions.abort("ConcatenatingMessageStream doesn't support explicitly empty streams");
        return null;
    }

    @Override
    protected MessageStream<Message> failingTestSubject(List<Message> messages, RuntimeException failure) {
        return completedTestSubject(messages).concatWith(MessageStream.failed(failure));
    }

    @Override
    protected Message createRandomMessage() {
        return new GenericMessage(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Nested
    class Peek {

        @Test
        void advancesToFirstMatchingEntry() {
            Message first = new GenericMessage(new MessageType("type"), "skip");
            Message second = new GenericMessage(new MessageType("type"), "keep");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate,
                                                                                  entry -> entry.message()
                                                                                                .payload()
                                                                                                .equals("keep"));

            Optional<Entry<Message>> peeked = stream.peek();
            Optional<Entry<Message>> next = stream.next();
            Optional<Entry<Message>> after = stream.next();

            assertThat(peeked).isPresent();
            assertThat(peeked.get().message().payload()).isEqualTo("keep");
            assertThat(next).isPresent();
            assertThat(next.get().message().payload()).isEqualTo("keep");
            assertThat(after).isNotPresent();
        }

        @Test
        void returnsEmptyForStreamWithOnlyNonMatchingElements() {
            Message first = new GenericMessage(new MessageType("type"), "skip1");
            Message second = new GenericMessage(new MessageType("type"), "skip2");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate,
                                                                                  entry -> entry.message()
                                                                                                .payloadAs(String.class)
                                                                                                .startsWith("keep"));

            assertThat(stream.peek()).isNotPresent();
        }

        @Test
        void returnsEmptyForEmptyStream() {
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of());
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.peek()).isNotPresent();
        }
    }

    @Nested
    class Next {

        @Test
        void skipsFilteredOutEntriesAndReturnsOnlyMatching() {
            Message first = new GenericMessage(new MessageType("type"), "skip1");
            Message second = new GenericMessage(new MessageType("type"), "keep1");
            Message third = new GenericMessage(new MessageType("type"), "skip2");
            Message fourth = new GenericMessage(new MessageType("type"), "keep2");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first, second, third, fourth));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate,
                                                                                  entry -> entry.message()
                                                                                                .payloadAs(String.class)
                                                                                                .startsWith("keep"));

            Optional<Entry<Message>> firstMatch = stream.next();
            assertThat(firstMatch).isPresent();
            assertThat(firstMatch.get().message().payload()).isEqualTo("keep1");

            Optional<Entry<Message>> secondMatch = stream.next();
            assertThat(secondMatch).isPresent();
            assertThat(secondMatch.get().message().payload()).isEqualTo("keep2");

            Optional<Entry<Message>> after = stream.next();
            assertThat(after).isNotPresent();
        }

        @Test
        void returnsEmptyForStreamWithOnlyNonMatchingElements() {
            Message first = new GenericMessage(new MessageType("type"), "skip1");
            Message second = new GenericMessage(new MessageType("type"), "skip2");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate,
                                                                                  entry -> entry.message()
                                                                                                .payloadAs(String.class)
                                                                                                .startsWith("keep"));

            assertThat(stream.next()).isNotPresent();
        }

        @Test
        void returnsEmptyForEmptyStream() {
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of());
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.next()).isNotPresent();
        }

        @Test
        void returnsAllEntriesIfAllMatch() {
            Message first = new GenericMessage(new MessageType("type"), "keep1");
            Message second = new GenericMessage(new MessageType("type"), "keep2");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> true);

            assertThat(stream.next()).isPresent();
            assertThat(stream.next()).isPresent();
            assertThat(stream.next()).isNotPresent();
        }
    }

    @Nested
    class HasNextAvailable {

        @Test
        void returnsTrueIfMatchingEntryAvailable() {
            Message first = new GenericMessage(new MessageType("type"), "keep");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.hasNextAvailable()).isTrue();
        }

        @Test
        void returnsFalseIfNoMatchingEntryAvailable() {
            Message first = new GenericMessage(new MessageType("type"), "skip");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> false);
            assertThat(stream.hasNextAvailable()).isFalse();
        }

        @Test
        void returnsFalseForEmptyStream() {
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of());
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.hasNextAvailable()).isFalse();
        }
    }

    @Nested
    class ErrorAndCompletion {

        @Test
        void propagatesErrorFromDelegate() {
            Exception failure = new RuntimeException("fail");
            MessageStream<Message> delegate = MessageStream.failed(failure);
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.error()).contains(failure);
        }

        @Test
        void isCompletedReflectsDelegateAndPeeked() {
            Message first = new GenericMessage(new MessageType("type"), "keep");
            MessageStream<Message> delegate = MessageStream.fromIterable(List.of(first));
            FilteringMessageStream<Message> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.isCompleted()).isFalse();
            assertThat(stream.next()).isPresent();
            assertThat(stream.next()).isEmpty(); // only completes, when no more entries
            assertThat(stream.isCompleted()).isTrue();
        }
    }
}