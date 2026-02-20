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

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MergedMessageStreamTest extends MessageStreamTest<Message> {

    @Override
    protected MessageStream<Message> completedTestSubject(List<Message> messages) {
        List<Message> first = new ArrayList<>();
        List<Message> second = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if ((i & 1) == 0) {
                first.add(messages.get(i));
            } else {
                second.add(messages.get(i));
            }
        }
        return new MergedMessageStream<>(positionComparator(messages), MessageStream.fromIterable(first),
                                         MessageStream.fromIterable(second));
    }

    private Comparator<MessageStream.Entry<Message>> positionComparator(List<Message> messages) {
        return Comparator.comparingInt(e -> messages.indexOf(e.message()));
    }

    @Override
    protected MessageStream.Single<Message> completedSingleStreamTestSubject(Message message) {
        return new MergedMessageStream<>(Comparator.comparing(m -> m.message().identifier()),
                                         MessageStream.just(message),
                                         MessageStream.empty())
                .first();
    }

    @Override
    protected MessageStream.Empty<Message> completedEmptyStreamTestSubject() {
        return new MergedMessageStream<>(Comparator.comparing(m -> m.message().identifier()),
                                         MessageStream.empty(),
                                         MessageStream.empty())
                .ignoreEntries();
    }

    @Override
    protected MessageStream<Message> failingTestSubject(List<Message> messages, RuntimeException failure) {
        List<Message> first = new ArrayList<>();
        List<Message> second = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if ((i & 1) == 0) {
                first.add(messages.get(i));
            } else {
                second.add(messages.get(i));
            }
        }
        return new MergedMessageStream<>(positionComparator(messages),
                                         MessageStream.fromIterable(first).concatWith(MessageStream.failed(failure)),
                                         MessageStream.fromIterable(second));
    }

    @Override
    protected Message createRandomMessage() {
        return new GenericMessage(new MessageType("message"), "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void errorIsReturnedWhenFirstStreamHasErrorEvenIfNotCompleted() {
        RuntimeException testException = new RuntimeException("Test error in first stream");
        MessageStream<Message> firstStream = MessageStream.failed(testException);
        MessageStream<Message> secondStream = MessageStream.fromItems(
                new GenericMessage(new MessageType("message"), "message1")
        );

        MergedMessageStream<Message> testSubject = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                firstStream,
                secondStream
        );

        // Error should be returned immediately, even though second stream is not completed
        Optional<Throwable> error = testSubject.error();
        assertTrue(error.isPresent(), "Error should be present when first stream has error");
        assertSame(testException, error.get(), "Should return the exact exception from first stream");
        assertFalse(testSubject.isCompleted(), "Merged stream should not be completed yet");
    }

    @Test
    void errorIsReturnedWhenSecondStreamHasErrorEvenIfNotCompleted() {
        RuntimeException testException = new RuntimeException("Test error in second stream");
        MessageStream<Message> firstStream = MessageStream.fromItems(
                new GenericMessage(new MessageType("message"), "message1")
        );
        MessageStream<Message> secondStream = MessageStream.failed(testException);

        MergedMessageStream<Message> testSubject = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                firstStream,
                secondStream
        );

        // Error should be returned, prioritizing first stream (which has none), then second
        Optional<Throwable> error = testSubject.error();
        assertTrue(error.isPresent(), "Error should be present when second stream has error");
        assertSame(testException, error.get(), "Should return the exact exception from second stream");
        assertFalse(testSubject.isCompleted(), "Merged stream should not be completed yet");
    }

    @Test
    void errorFromFirstStreamTakesPrecedenceOverSecondStream() {
        RuntimeException firstException = new RuntimeException("First stream error");
        RuntimeException secondException = new RuntimeException("Second stream error");
        MessageStream<Message> firstStream = MessageStream.failed(firstException);
        MessageStream<Message> secondStream = MessageStream.failed(secondException);

        MergedMessageStream<Message> testSubject = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                firstStream,
                secondStream
        );

        Optional<Throwable> error = testSubject.error();
        assertTrue(error.isPresent());
        assertSame(firstException, error.get(), "First stream error should take precedence");
    }

    @SuppressWarnings("unchecked")
    @Test
    void callbackIsRegisteredWithBothUnderlyingStreams() {
        MessageStream<Message> firstStream = mock(MessageStream.class);
        MessageStream<Message> secondStream = mock(MessageStream.class);

        MergedMessageStream<Message> testSubject = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                firstStream,
                secondStream
        );

        Runnable callback = () -> {
        };

        ArgumentCaptor<Runnable> rc = ArgumentCaptor.forClass(Runnable.class);
        testSubject.setCallback(callback);


        verify(firstStream).setCallback(rc.capture());
        verify(secondStream).setCallback(rc.capture());

        assertSame(rc.getAllValues().get(0), rc.getAllValues().get(1));
    }

    @Test
    void hasNextAvailableReturnsTrueWhenEitherStreamHasMessages() {
        MessageStream<Message> emptyStream = MessageStream.empty();
        MessageStream<Message> nonEmptyStream = MessageStream.just(
                new GenericMessage(new MessageType("message"), "message1")
        );

        MergedMessageStream<Message> testSubject1 = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                nonEmptyStream,
                emptyStream
        );

        MergedMessageStream<Message> testSubject2 = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                emptyStream,
                nonEmptyStream
        );

        assertTrue(testSubject1.hasNextAvailable(), "Should return true when first stream has messages");
        assertTrue(testSubject2.hasNextAvailable(), "Should return true when second stream has messages");
    }

    @Test
    void isCompletedOnlyWhenBothStreamsAreCompleted() {
        MessageStream<Message> completedStream = MessageStream.empty();
        MessageStream<Message> incompleteStream = MessageStream.just(createRandomMessage());

        MergedMessageStream<Message> testSubject1 = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                completedStream,
                incompleteStream
        );

        MergedMessageStream<Message> testSubject2 = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                incompleteStream,
                completedStream
        );

        MergedMessageStream<Message> testSubject3 = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                completedStream,
                completedStream
        );

        assertFalse(testSubject1.isCompleted(), "Should not be completed when second stream is incomplete");
        assertFalse(testSubject2.isCompleted(), "Should not be completed when first stream is incomplete");
        assertTrue(testSubject3.isCompleted(), "Should be completed when both streams are completed");
    }

    @Test
    void messagesAreMergedAccordingToComparator() {
        Message message1 = new GenericMessage(new MessageType("message"), "a-message");
        Message message2 = new GenericMessage(new MessageType("message"), "b-message");
        Message message3 = new GenericMessage(new MessageType("message"), "c-message");

        MessageStream<Message> firstStream = MessageStream.fromItems(message1, message3);
        MessageStream<Message> secondStream = MessageStream.fromItems(message2);

        // Comparator that sorts by payload (alphabetically)
        Comparator<MessageStream.Entry<Message>> payloadComparator =
                Comparator.comparing(e -> e.message().payloadAs(String.class), String.CASE_INSENSITIVE_ORDER);

        MergedMessageStream<Message> testSubject = new MergedMessageStream<>(
                payloadComparator,
                firstStream,
                secondStream
        );

        // Should return messages in order: a, b, c
        Optional<MessageStream.Entry<Message>> first = testSubject.next();
        assertTrue(first.isPresent());
        assertEquals("a-message", first.get().message().payload());

        Optional<MessageStream.Entry<Message>> second = testSubject.next();
        assertTrue(second.isPresent());
        assertEquals("b-message", second.get().message().payload());

        Optional<MessageStream.Entry<Message>> third = testSubject.next();
        assertTrue(third.isPresent());
        assertEquals("c-message", third.get().message().payload());
    }

    @Test
    void closingMergedStreamClosesBothUnderlyingStreams() {
        AtomicBoolean firstClosed = new AtomicBoolean(false);
        AtomicBoolean secondClosed = new AtomicBoolean(false);

        Message message1 = new GenericMessage(new MessageType("message"), "message1");
        Message message2 = new GenericMessage(new MessageType("message"), "message2");

        MessageStream<Message> firstStream = MessageStream.fromItems(message1)
                                                          .onClose(() -> firstClosed.set(true));

        MessageStream<Message> secondStream = MessageStream.fromItems(message2)
                                                           .onClose(() -> secondClosed.set(true));

        MergedMessageStream<Message> testSubject = new MergedMessageStream<>(
                Comparator.comparing(e -> e.message().identifier()),
                firstStream,
                secondStream
        );

        testSubject.close();

        assertTrue(firstClosed.get(), "First stream should be closed");
        assertTrue(secondClosed.get(), "Second stream should be closed");
    }
}