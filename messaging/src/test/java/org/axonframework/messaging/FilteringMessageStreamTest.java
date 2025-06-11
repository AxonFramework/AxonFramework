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

import static org.assertj.core.api.Assertions.assertThat;

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

    @Nested
    class Peek {

        @Test
        void advancesToFirstMatchingEntry() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "skip");
            Message<String> second = new GenericMessage<>(new MessageType("type"), "keep");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate,
                                                                                          entry -> entry.message()
                                                                                                        .getPayload()
                                                                                                        .equals("keep"));

            Optional<Entry<Message<String>>> peeked = stream.peek();
            Optional<Entry<Message<String>>> next = stream.next();
            Optional<Entry<Message<String>>> after = stream.next();

            assertThat(peeked).isPresent();
            assertThat(peeked.get().message().getPayload()).isEqualTo("keep");
            assertThat(next).isPresent();
            assertThat(next.get().message().getPayload()).isEqualTo("keep");
            assertThat(after).isNotPresent();
        }

        @Test
        void returnsEmptyForStreamWithOnlyNonMatchingElements() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "skip1");
            Message<String> second = new GenericMessage<>(new MessageType("type"), "skip2");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate,
                                                                                          entry -> entry.message()
                                                                                                        .getPayload()
                                                                                                        .startsWith(
                                                                                                                "keep"));

            assertThat(stream.peek()).isNotPresent();
        }

        @Test
        void returnsEmptyForEmptyStream() {
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of());
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.peek()).isNotPresent();
        }
    }

    @Nested
    class Next {

        @Test
        void skipsFilteredOutEntriesAndReturnsOnlyMatching() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "skip1");
            Message<String> second = new GenericMessage<>(new MessageType("type"), "keep1");
            Message<String> third = new GenericMessage<>(new MessageType("type"), "skip2");
            Message<String> fourth = new GenericMessage<>(new MessageType("type"), "keep2");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second, third, fourth));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate,
                                                                                          entry -> entry.message()
                                                                                                        .getPayload()
                                                                                                        .startsWith(
                                                                                                                "keep"));

            Optional<Entry<Message<String>>> firstMatch = stream.next();
            assertThat(firstMatch).isPresent();
            assertThat(firstMatch.get().message().getPayload()).isEqualTo("keep1");

            Optional<Entry<Message<String>>> secondMatch = stream.next();
            assertThat(secondMatch).isPresent();
            assertThat(secondMatch.get().message().getPayload()).isEqualTo("keep2");

            Optional<Entry<Message<String>>> after = stream.next();
            assertThat(after).isNotPresent();
        }

        @Test
        void returnsEmptyForStreamWithOnlyNonMatchingElements() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "skip1");
            Message<String> second = new GenericMessage<>(new MessageType("type"), "skip2");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate,
                                                                                          entry -> entry.message()
                                                                                                        .getPayload()
                                                                                                        .startsWith(
                                                                                                                "keep"));

            assertThat(stream.next()).isNotPresent();
        }

        @Test
        void returnsEmptyForEmptyStream() {
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of());
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.next()).isNotPresent();
        }

        @Test
        void returnsAllEntriesIfAllMatch() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "keep1");
            Message<String> second = new GenericMessage<>(new MessageType("type"), "keep2");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first, second));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> true);

            assertThat(stream.next()).isPresent();
            assertThat(stream.next()).isPresent();
            assertThat(stream.next()).isNotPresent();
        }
    }

    @Nested
    class HasNextAvailable {

        @Test
        void returnsTrueIfMatchingEntryAvailable() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "keep");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.hasNextAvailable()).isTrue();
        }

        @Test
        void returnsFalseIfNoMatchingEntryAvailable() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "skip");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> false);
            assertThat(stream.hasNextAvailable()).isFalse();
        }

        @Test
        void returnsFalseForEmptyStream() {
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of());
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.hasNextAvailable()).isFalse();
        }
    }

    @Nested
    class ErrorAndCompletion {

        @Test
        void propagatesErrorFromDelegate() {
            Exception failure = new RuntimeException("fail");
            MessageStream<Message<String>> delegate = MessageStream.failed(failure);
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.error()).contains(failure);
        }

        @Test
        void isCompletedReflectsDelegateAndPeeked() {
            Message<String> first = new GenericMessage<>(new MessageType("type"), "keep");
            MessageStream<Message<String>> delegate = MessageStream.fromIterable(List.of(first));
            FilteringMessageStream<Message<String>> stream = new FilteringMessageStream<>(delegate, entry -> true);
            assertThat(stream.isCompleted()).isFalse();
            stream.next();
            assertThat(stream.isCompleted()).isTrue();
        }
    }
}