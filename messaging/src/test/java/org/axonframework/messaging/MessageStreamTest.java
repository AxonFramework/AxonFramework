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

import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite used to validate implementations of the {@link MessageStream}.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of the {@link MessageStream stream}
 *            under test.
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public abstract class MessageStreamTest<M extends Message<?>> {

    /**
     * Construct a test subject using the given {@code messages} as the source.
     * <p>
     * It is the task of the implementer of this method to map the {@code messages} to {@link Entry entires} for the
     * {@link MessageStream stream} under test.
     *
     * @param messages The {@link Message Message} of type {@code M} acting as the source for the
     *                 {@link MessageStream stream} under construction.
     * @return A {@link MessageStream stream} to use for testing.
     */
    abstract MessageStream<M> completedTestSubject(List<M> messages);

    /**
     * Construct a test subject using the given {@code messages} as the source. The resulting stream should not report
     * as completed if given messages are consumed.
     * <p>
     * It is the task of the implementer of this method to map the {@code messages} to {@link Entry entires} for the
     * {@link MessageStream stream} under test.
     *
     * @param messages The {@link Message Message} of type {@code M} acting as the source for the
     *                 {@link MessageStream stream} under construction.
     * @return A {@link MessageStream stream} to use for testing.
     */
    protected MessageStream<M> uncompletedTestSubject(List<M> messages) {
        return completedTestSubject(messages).concatWith(MessageStream.fromFuture(new CompletableFuture<>()));
    }

    /**
     * Construct a test subject using the given {@code messages} as the source, which will fail due to the given
     * {@code failure}.
     * <p>
     * It is the task of the implementer of this method to map the {@code messages} to {@link Entry entires} for the
     * {@link MessageStream stream} under test.
     *
     * @param messages The {@link Message Message} of type {@code M} acting as the source for the
     *                 {@link MessageStream stream} under construction.
     * @param failure  The {@link Exception} causing the {@link MessageStream stream} under construction to fail.
     * @return A {@link MessageStream stream} that will complete exceptionally to use for testing.
     */
    abstract MessageStream<M> failingTestSubject(List<M> messages,
                                                 Exception failure);

    protected void publishAdditionalMessage(MessageStream<M> testSubject, M randomMessage) {
        Assumptions.abort("This implementation doesn't support delayed publishing");
    }

    /**
     * Constructs a random {@link Message} of type {@code M} to be used during testing.
     *
     * @return A random {@link Message} of type {@code M} to be used during testing.
     */
    abstract M createRandomMessage();

    @Test
    void shouldInvokeOnAvailableCallbackWhenMessagesAreAvailable() {
        MessageStream<M> testSubject = completedTestSubject(List.of(createRandomMessage()));

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnAvailableCallbackWhenStreamIsCompleted() {
        MessageStream<M> testSubject = completedTestSubject(List.of());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnAvailableCallbackWhenCompleted() {
        MessageStream<M> testSubject = failingTestSubject(List.of(), new RuntimeException("Oops"));

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeOnAvailableCallbackWhenNotCompleted() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
    }

    @Test
    void shouldNotInvokeOnAvailableCallbackWhenNotCompletedAndAvailableMessageIsConsumed() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(createRandomMessage()));

        assertTrue(testSubject.next().isPresent());
        assertFalse(testSubject.next().isPresent());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
    }

    @Test
    void shouldNotInvokeOnAvailableCallbackWhenNotCompletedAndAvailableMessagesAreConsumed() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(createRandomMessage(), createRandomMessage()));

        assertTrue(testSubject.next().isPresent());
        assertTrue(testSubject.next().isPresent());
        assertFalse(testSubject.next().isPresent());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
    }

    @Test
    void shouldInvokeOnAvailableCallbackWhenNotCompletedAndNotAllAvailableMessagesAreConsumed() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(createRandomMessage(), createRandomMessage()));

        assertTrue(testSubject.next().isPresent());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void shouldEmitOriginalExceptionAsFailure() {
        MessageStream<M> testSubject = failingTestSubject(List.of(), new MockException());

        CompletableFuture<Entry<M>> actual = testSubject.firstAsCompletableFuture();

        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }

    @Test
    void shouldCompleteWithNullOnEmptyList() {
        MessageStream<M> testSubject = completedTestSubject(Collections.emptyList());

        CompletableFuture<Entry<M>> actual = testSubject.firstAsCompletableFuture();

        assertNull(actual.resultNow());
    }

    @Test
    void shouldMapSingleEntry_asCompletableFuture() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in));

        var actual = testSubject.map(entry -> entry.map(input -> out))
                                .firstAsCompletableFuture()
                                .join()
                                .message();

        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleMessage_asCompletableFuture() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in));

        var actual = testSubject.mapMessage(input -> out)
                                .firstAsCompletableFuture()
                                .join()
                                .message();

        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleEntry_asFlux() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in));

        StepVerifier.create(testSubject.map(entry -> entry.map(input -> out)).asFlux())
                    .expectNextMatches(entry -> entry.message().equals(out))
                    .verifyComplete();
    }

    @Test
    void shouldMapSingleMessage_asFlux() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in));

        StepVerifier.create(testSubject.mapMessage(input -> out).asFlux())
                    .expectNextMatches(entry -> entry.message().equals(out))
                    .verifyComplete();
    }

    @Test
    void shouldMapMultipleEntries_asFlux() {
        M in1 = createRandomMessage();
        M out1 = createRandomMessage();
        M in2 = createRandomMessage();
        M out2 = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in1, in2));

        StepVerifier.create(testSubject.map(entry -> entry.map(input -> input == in1 ? out1 : out2)).asFlux())
                    .expectNextMatches(entry -> entry.message().equals(out1))
                    .expectNextMatches(entry -> entry.message().equals(out2))
                    .verifyComplete();
    }

    @Test
    void shouldMapMultipleMessages_asFlux() {
        M in1 = createRandomMessage();
        M out1 = createRandomMessage();
        M in2 = createRandomMessage();
        M out2 = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in1, in2));

        StepVerifier.create(testSubject.mapMessage(input -> input == in1 ? out1 : out2).asFlux())
                    .expectNextMatches(entry -> entry.message().equals(out1))
                    .expectNextMatches(entry -> entry.message().equals(out2))
                    .verifyComplete();
    }

    @Test
    void shouldMapEntriesUntilFailure_asFlux() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = failingTestSubject(List.of(in), new MockException())
                .map(entry -> entry.map(input -> out))
                .onErrorContinue(MessageStream::failed);

        StepVerifier.create(testSubject.asFlux())
                    .expectNextMatches(entry -> entry.message().equals(out))
                    .expectErrorMatches(MockException.class::isInstance)
                    .verify();
    }

    @Test
    void shouldMapMessagesUntilFailure_asFlux() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = failingTestSubject(List.of(in), new MockException())
                .mapMessage(input -> out)
                .onErrorContinue(MessageStream::failed);

        StepVerifier.create(testSubject.asFlux())
                    .expectNextMatches(entry -> entry.message().equals(out))
                    .expectErrorMatches(MockException.class::isInstance)
                    .verify();
    }

    @Test
    void shouldNotCallMapperForEmptyStream_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of())
                .map(entry -> {
                    invoked.set(true);
                    return entry;
                });

        Entry<M> actual = testSubject.firstAsCompletableFuture().join();

        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
        assertNull(actual, "Expected null value from empty stream");
    }

    @Test
    void shouldNotCallMessageMapperForEmptyStream_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of())
                .mapMessage(message -> {
                    invoked.set(true);
                    return message;
                });

        Entry<M> actual = testSubject.firstAsCompletableFuture().join();

        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
        assertNull(actual, "Expected null value from empty stream");
    }

    @Test
    void shouldNotCallMapperForEmptyStream_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of())
                .map(entry -> {
                    invoked.set(true);
                    return entry;
                });

        StepVerifier.create(testSubject.asFlux())
                    .verifyComplete();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMessageMapperForEmptyStream_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of())
                .mapMessage(message -> {
                    invoked.set(true);
                    return message;
                });

        StepVerifier.create(testSubject.asFlux())
                    .verifyComplete();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMapperForFailedStream() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = failingTestSubject(List.of(), new MockException())
                .map(entry -> {
                    invoked.set(true);
                    return entry;
                });

        assertTrue(testSubject.firstAsCompletableFuture().isCompletedExceptionally());
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMessageMapperForFailedStream() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = failingTestSubject(List.of(), new MockException())
                .mapMessage(message -> {
                    invoked.set(true);
                    return message;
                });

        assertTrue(testSubject.firstAsCompletableFuture().isCompletedExceptionally());
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldReduceToExpectedResult() {
        M randomMessage = createRandomMessage();
        String expected = randomMessage.getPayload().toString() + randomMessage.getPayload().toString();

        MessageStream<M> testSubject = completedTestSubject(List.of(randomMessage, randomMessage));

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> entry.message().getPayload().toString() + entry.message().getPayload().toString()
        );

        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldReturnIdentityWhenReducingEmptyStream() {
        String expected = "42";
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of());

        CompletableFuture<String> result = testSubject.reduce(
                expected,
                (base, entry) -> {
                    invoked.set(true);
                    return entry.message().getPayload().toString() + entry.message().getPayload().toString();
                }
        );

        assertTrue(result.isDone());
        assertEquals(expected, result.join());
        assertFalse(invoked.get());
    }

    @Test
    void shouldNotReduceForEmptyFailingStream() {
        AtomicBoolean invoked = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<M> testSubject = failingTestSubject(List.of(), expected);

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> {
                    invoked.set(true);
                    return entry.message().getPayload().toString() + entry.message().getPayload().toString();
                }
        );

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertFalse(invoked.get());
    }

    @Test
    void shouldCompleteExceptionallyAfterReducingForFailedStream() {
        AtomicBoolean invoked = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<M> testSubject =
                failingTestSubject(List.of(createRandomMessage(), createRandomMessage()), expected);

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> {
                    invoked.set(true);
                    return entry.message().getPayload().toString() + entry.message().getPayload().toString();
                }
        );

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnNextHandler_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        M expected = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(expected));

        CompletableFuture<Entry<M>> result = testSubject.onNext(entry -> invoked.set(true))
                                                        .firstAsCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join().message());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnNextHandler_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        M expected = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(expected));

        StepVerifier.create(testSubject.onNext(entry -> invoked.set(true))
                                       .asFlux())
                    .expectNextMatches(entry -> entry.message().equals(expected))
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldReturnFirstEntryFromOnErrorStream_asCompletableFuture() {
        Exception expectedError = new RuntimeException("oops");
        M expected = createRandomMessage();
        MessageStream<M> onErrorStream = completedTestSubject(List.of(expected));

        MessageStream<M> testSubject = failingTestSubject(List.of(), expectedError);

        CompletableFuture<Entry<M>> result =
                testSubject.onErrorContinue(error -> {
                               assertEquals(expectedError, FutureUtils.unwrap(error));
                               return onErrorStream;
                           })
                           .firstAsCompletableFuture();

        assertTrue(result.isDone());
        assertEquals(expected, result.join().message());
    }

    @Test
    void shouldContinueOnSecondStreamOnError_asFlux() {
        Exception expectedError = new RuntimeException("oops");
        M expectedFirst = createRandomMessage();
        M expectedSecond = createRandomMessage();
        MessageStream<M> onErrorStream = completedTestSubject(List.of(expectedSecond));

        MessageStream<M> testSubject = failingTestSubject(List.of(expectedFirst), expectedError);

        StepVerifier.create(testSubject.onErrorContinue(error -> {
                                           assertEquals(expectedError, FutureUtils.unwrap(error));
                                           return onErrorStream;
                                       })
                                       .asFlux())
                    .expectNextMatches(entry -> entry.message().equals(expectedFirst))
                    .expectNextMatches(entry -> entry.message().equals(expectedSecond))
                    .verifyComplete();
    }

    @Test
    void shouldMoveToConcatWithStream_asCompletableFuture_returnFirstEntryFromFirstStream() {
        M expected = createRandomMessage();
        MessageStream<M> secondStream = completedTestSubject(List.of());

        MessageStream<M> firstStream = completedTestSubject(List.of(expected));

        CompletableFuture<Entry<M>> result = firstStream.concatWith(secondStream)
                                                        .firstAsCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join().message());
    }

    @Test
    void shouldMoveToConcatWithStream_asCompletableFuture_returnFirstEntryFromSecondStream() {
        M expected = createRandomMessage();
        MessageStream<M> secondStream = completedTestSubject(List.of(expected));

        MessageStream<M> firstStream = completedTestSubject(List.of());

        CompletableFuture<Entry<M>> result = firstStream.concatWith(secondStream)
                                                        .firstAsCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join().message());
    }

    @Test
    void shouldMoveToConcatWithStream_asFlux() {
        M expectedFirst = createRandomMessage();
        M expectedSecond = createRandomMessage();
        MessageStream<M> secondStream = completedTestSubject(List.of(expectedSecond));

        MessageStream<M> testSubject = completedTestSubject(List.of(expectedFirst));

        StepVerifier.create(testSubject.concatWith(secondStream).asFlux())
                    .expectNextMatches(entry -> entry.message().equals(expectedFirst))
                    .expectNextMatches(entry -> entry.message().equals(expectedSecond))
                    .verifyComplete();
    }

    @Test
    void shouldInvokeCompletionCallback_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of());

        testSubject.whenComplete(() -> invoked.set(true))
                   .firstAsCompletableFuture()
                   .join();

        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<M> testSubject = failingTestSubject(List.of(), expected);

        CompletableFuture<Entry<M>> result = testSubject.whenComplete(() -> invoked.set(true))
                                                        .firstAsCompletableFuture();
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertFalse(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallback_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of());

        StepVerifier.create(testSubject.whenComplete(() -> invoked.set(true))
                                       .asFlux())
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        RuntimeException oops = new RuntimeException("oops");
        MessageStream<M> testSubject = failingTestSubject(List.of(), oops);

        StepVerifier.create(testSubject.whenComplete(() -> invoked.set(true))
                                       .asFlux())
                    .verifyErrorMatches(e -> e == oops);
        assertFalse(invoked.get());
    }

    @Test
    void shouldResultInFailedStreamWhenCompletionCallbackThrowsAnException_asCompletableFuture() {
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<M> testSubject = completedTestSubject(List.of(createRandomMessage()));

        CompletableFuture<Entry<M>> result = testSubject.whenComplete(() -> {
                                                            throw expected;
                                                        })
                                                        .firstAsCompletableFuture();

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
    }

    @Test
    void shouldResultInFailedStreamWhenCompletionCallbackThrowsAnException_asFlux() {
        RuntimeException expected = new RuntimeException("oops");
        M expectedMessage = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(expectedMessage));

        StepVerifier.create(testSubject.whenComplete(() -> {
                                           throw expected;
                                       })
                                       .asFlux())
                    .expectNextMatches(entry -> entry.message().equals(expectedMessage))
                    .verifyErrorMatches(expected::equals);
    }

    @Test
    void shouldInvokeCallbackOnceAdditionalMessagesBecomeAvailable() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = uncompletedTestSubject(List.of());
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
        publishAdditionalMessage(testSubject, createRandomMessage());
        assertTrue(invoked.get());
    }

}
