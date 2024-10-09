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
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite used to validate implementations of the {@link MessageStream}
 *
 * @param <E> The type of entry carried in the {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public abstract class MessageStreamTest<E> {

    /**
     * Construct a test subject using the given {@code entries} as the source.
     *
     * @param entries The entries of type {@code E} acting as the source for the {@link MessageStream stream} under
     *                construction.
     * @return A {@link MessageStream stream} to use for testing.
     */
    abstract MessageStream<E> testSubject(List<E> entries);

    /**
     * Construct a test subject using the given {@code entries} as the source, which will fail due to the given
     * {@code failure}.
     *
     * @param entries The entries of type {@code E} acting as the source for the {@link MessageStream stream} under
     *                construction.
     * @param failure The {@link Exception} causing the {@link MessageStream stream} under construction to fail.
     * @return A {@link MessageStream stream} that will complete exceptionally to use for testing.
     */
    abstract MessageStream<E> failingTestSubject(List<E> entries,
                                                 Exception failure);

    /**
     * Constructs a random entry of type {@code E} to be used during testing.
     *
     * @return A random entry of type {@code E} to be used during testing.
     */
    abstract E createRandomEntry();

    @Test
    void shouldEmitOriginalExceptionAsFailure() {
        MessageStream<E> testSubject = failingTestSubject(List.of(), new MockException());

        CompletableFuture<E> actual = testSubject.asCompletableFuture();

        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }

    @Test
    void shouldCompleteWithNullOnEmptyList() {
        MessageStream<E> testSubject = testSubject(Collections.emptyList());

        CompletableFuture<E> actual = testSubject.asCompletableFuture();

        assertNull(actual.resultNow());
    }

    @Test
    void shouldMapSingleValue_asCompletableFuture() {
        E in = createRandomEntry();
        E out = createRandomEntry();

        MessageStream<E> testSubject = testSubject(List.of(in));

        var actual = testSubject.map(input -> out).asCompletableFuture().join();

        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleValue_asFlux() {
        E in = createRandomEntry();
        E out = createRandomEntry();

        MessageStream<E> testSubject = testSubject(List.of(in));

        StepVerifier.create(testSubject.map(input -> out).asFlux())
                    .expectNext(out)
                    .verifyComplete();
    }

    @Test
    void shouldMapMultipleValues_asFlux() {
        E in1 = createRandomEntry();
        E out1 = createRandomEntry();
        E in2 = createRandomEntry();
        E out2 = createRandomEntry();

        MessageStream<E> testSubject = testSubject(List.of(in1, in2));

        StepVerifier.create(testSubject.map(input -> input == in1 ? out1 : out2).asFlux())
                    .expectNext(out1, out2)
                    .verifyComplete();
    }

    @Test
    void shouldMapValuesUntilFailure_asFlux() {
        E in = createRandomEntry();
        E out = createRandomEntry();

        MessageStream<E> testSubject = failingTestSubject(List.of(in), new MockException())
                .map(input -> out)
                .onErrorContinue(MessageStream::failed);

        StepVerifier.create(testSubject.asFlux())
                    .expectNextMatches(out::equals)
                    .expectErrorMatches(MockException.class::isInstance)
                    .verify();
    }

    @Test
    void shouldNotCallMapperForEmptyStream_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<E> testSubject = testSubject(List.of())
                .map(i -> {
                    invoked.set(true);
                    return i;
                });

        Object actual = testSubject.asCompletableFuture().join();

        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
        assertNull(actual, "Expected null value from empty stream");
    }

    @Test
    void shouldNotCallMapperForEmptyStream_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<E> testSubject = testSubject(List.of())
                .map(i -> {
                    invoked.set(true);
                    return i;
                });

        StepVerifier.create(testSubject.asFlux())
                    .verifyComplete();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMapperForFailedStream() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<E> testSubject = failingTestSubject(List.of(), new MockException())
                .map(i -> {
                    invoked.set(true);
                    return i;
                });

        assertTrue(testSubject.asCompletableFuture().isCompletedExceptionally());
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldReduceToExpectedResult() {
        E randomPayload = createRandomEntry();
        String expected = randomPayload.toString() + randomPayload;

        MessageStream<E> testSubject = testSubject(List.of(randomPayload, randomPayload));

        CompletableFuture<String> result = testSubject.reduce("", (base, entry) -> entry.toString() + entry);

        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldReturnIdentityWhenReducingEmptyStream() {
        String expected = "42";
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<E> testSubject = testSubject(List.of());

        CompletableFuture<String> result = testSubject.reduce(
                expected,
                (base, entry) -> {
                    invoked.set(true);
                    return entry.toString() + entry;
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

        MessageStream<E> testSubject = failingTestSubject(List.of(), expected);

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> {
                    invoked.set(true);
                    return entry.toString() + entry;
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

        MessageStream<E> testSubject = failingTestSubject(
                List.of(createRandomEntry(),
                        createRandomEntry()),
                expected
        );

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> {
                    invoked.set(true);
                    return entry.toString() + entry;
                }
        );

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnNextHandler_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        E expected = createRandomEntry();

        MessageStream<E> testSubject = testSubject(List.of(expected));

        CompletableFuture<E> result = testSubject.onNextItem((entry) -> invoked.set(true))
                                                 .asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnNextHandler_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        E expected = createRandomEntry();

        MessageStream<E> testSubject = testSubject(List.of(expected));

        StepVerifier.create(testSubject.onNextItem((entry) -> invoked.set(true))
                                       .asFlux())
                    .expectNext(expected)
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldReturnFirstEntryFromOnErrorStream_asCompletableFuture() {
        Exception expectedError = new RuntimeException("oops");
        E expected = createRandomEntry();
        MessageStream<E> onErrorStream = testSubject(List.of(expected));

        MessageStream<E> testSubject = failingTestSubject(List.of(), expectedError);

        CompletableFuture<E> result = testSubject.onErrorContinue(error -> {
                                                     assertEquals(expectedError, FutureUtils.unwrap(error));
                                                     return onErrorStream;
                                                 })
                                                 .asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldContinueOnSecondStreamOnError_asFlux() {
        Exception expectedError = new RuntimeException("oops");
        E expectedFirst = createRandomEntry();
        E expectedSecond = createRandomEntry();
        MessageStream<E> onErrorStream = testSubject(List.of(expectedSecond));

        MessageStream<E> testSubject = failingTestSubject(List.of(expectedFirst), expectedError);

        StepVerifier.create(testSubject.onErrorContinue(error -> {
                                           assertEquals(expectedError, FutureUtils.unwrap(error));
                                           return onErrorStream;
                                       })
                                       .asFlux())
                    .expectNext(expectedFirst)
                    .expectNext(expectedSecond)
                    .verifyComplete();
    }

    @Test
    void shouldMoveToConcatWithStream_asCompletableFuture_returnFirstEntryFromFirstStream() {
        E expected = createRandomEntry();
        MessageStream<E> secondStream = testSubject(List.of());

        MessageStream<E> firstStream = testSubject(List.of(expected));

        CompletableFuture<E> result = firstStream.concatWith(secondStream)
                                                 .asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldMoveToConcatWithStream_asCompletableFuture_returnFirstEntryFromSecondStream() {
        E expected = createRandomEntry();
        MessageStream<E> secondStream = testSubject(List.of(expected));

        MessageStream<E> firstStream = testSubject(List.of());

        CompletableFuture<E> result = firstStream.concatWith(secondStream)
                                                 .asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldMoveToConcatWithStream_asFlux() {
        E expectedFirst = createRandomEntry();
        E expectedSecond = createRandomEntry();
        MessageStream<E> secondStream = testSubject(List.of(expectedSecond));

        MessageStream<E> testSubject = testSubject(List.of(expectedFirst));

        StepVerifier.create(testSubject.concatWith(secondStream).asFlux())
                    .expectNext(expectedFirst)
                    .expectNext(expectedSecond)
                    .verifyComplete();
    }

    @Test
    void shouldInvokeCompletionCallback_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<E> testSubject = testSubject(List.of());

        testSubject.whenComplete(() -> invoked.set(true))
                   .asCompletableFuture()
                   .join();

        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<E> testSubject = failingTestSubject(List.of(), expected);

        CompletableFuture<E> result = testSubject.whenComplete(() -> invoked.set(true))
                                                 .asCompletableFuture();
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertFalse(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallback_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<E> testSubject = testSubject(List.of());

        StepVerifier.create(testSubject.whenComplete(() -> invoked.set(true))
                                       .asFlux())
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<E> testSubject = failingTestSubject(List.of(), new RuntimeException("oops"));

        StepVerifier.create(testSubject.whenComplete(() -> invoked.set(true))
                                       .asFlux())
                    .verifyError(RuntimeException.class);
        assertFalse(invoked.get());
    }

    @Test
    void shouldResultInFailedStreamWhenCompletionCallbackThrowsAnException_asCompletableFuture() {
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<E> testSubject = testSubject(List.of(createRandomEntry()));

        CompletableFuture<E> result = testSubject.whenComplete(() -> {
                                                     throw expected;
                                                 })
                                                 .asCompletableFuture();

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
    }

    @Test
    void shouldResultInFailedStreamWhenCompletionCallbackThrowsAnException_asFlux() {
        RuntimeException expected = new RuntimeException("oops");
        E expectedEntry = createRandomEntry();

        MessageStream<E> testSubject = testSubject(List.of(expectedEntry));

        StepVerifier.create(testSubject.whenComplete(() -> {
                                           throw expected;
                                       })
                                       .asFlux())
                    .expectNext(expectedEntry)
                    .verifyErrorMatches(expected::equals);
    }
}
