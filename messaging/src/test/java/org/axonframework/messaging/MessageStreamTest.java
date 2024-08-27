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

import static org.axonframework.messaging.GenericMessage.asMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite used to validate implementations of the {@link MessageStream}
 *
 * @param <P> The payload type contained in the {@link Message Messages} carried in the {@link MessageStream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public abstract class MessageStreamTest<P> {

    /**
     * Construct a test subject using the given {@code messages} as the message source.
     *
     * @param messages The {@link Message Messages} acting as the source for the {@link MessageStream} under
     *                 construction.
     * @return A {@link MessageStream} to use for testing.
     */
    abstract MessageStream<Message<P>> testSubject(List<Message<P>> messages);

    /**
     * Construct a test subject using the given {@code messages} as the message source, which will fail due to the given
     * {@code failure}.
     *
     * @param messages The {@link Message Messages} acting as the source for the {@link MessageStream} under
     *                 construction.
     * @param failure  The {@link Exception} causing the {@link MessageStream} under construction to fail.
     * @return A {@link MessageStream} that will complete exceptionally to use for testing.
     */
    abstract MessageStream<Message<P>> failingTestSubject(List<Message<P>> messages,
                                                          Exception failure);

    /**
     * Constructs a random payload of type {@code P} for the {@link Message Messages} used use during testing.
     *
     * @return A random payload of type {@code P} for the {@link Message Messages} used use during testing.
     */
    abstract P createRandomValidEntry();

    @Test
    void shouldEmitOriginalExceptionAsFailure() {
        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(), new MockException());

        CompletableFuture<Message<P>> actual = testSubject.asCompletableFuture();

        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }

    @Test
    void shouldCompleteWithNullOnEmptyList() {
        MessageStream<Message<P>> testSubject = testSubject(Collections.emptyList());

        CompletableFuture<Message<P>> actual = testSubject.asCompletableFuture();

        assertNull(actual.resultNow());
    }

    @Test
    void shouldMapSingleValue_asCompletableFuture() {
        Message<P> in = asMessage(createRandomValidEntry());
        Message<P> out = asMessage(createRandomValidEntry());

        MessageStream<Message<P>> testSubject = testSubject(List.of(in));

        var actual = testSubject.map(input -> out).asCompletableFuture().join();

        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleValue_asFlux() {
        Message<P> in = asMessage(createRandomValidEntry());
        Message<P> out = asMessage(createRandomValidEntry());

        MessageStream<Message<P>> testSubject = testSubject(List.of(in));

        StepVerifier.create(testSubject.map(input -> out).asFlux())
                    .expectNext(out)
                    .verifyComplete();
    }

    @Test
    void shouldMapMultipleValues_asFlux() {
        Message<P> in1 = asMessage(createRandomValidEntry());
        Message<P> out1 = asMessage(createRandomValidEntry());
        Message<P> in2 = asMessage(createRandomValidEntry());
        Message<P> out2 = asMessage(createRandomValidEntry());

        MessageStream<Message<P>> testSubject = testSubject(List.of(in1, in2));

        StepVerifier.create(testSubject.map(input -> input == in1 ? out1 : out2).asFlux())
                    .expectNext(out1, out2)
                    .verifyComplete();
    }

    @Test
    void shouldMapValuesUntilFailure_asFlux() {
        Message<P> in = asMessage(createRandomValidEntry());
        Message<P> out = asMessage(createRandomValidEntry());

        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(in), new MockException())
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

        MessageStream<Message<P>> testSubject = testSubject(List.of())
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

        MessageStream<Message<P>> testSubject = testSubject(List.of())
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

        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(), new MockException())
                .map(i -> {
                    invoked.set(true);
                    return i;
                });

        assertTrue(testSubject.asCompletableFuture().isCompletedExceptionally());
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldReduceToExpectedResult() {
        P randomPayload = createRandomValidEntry();
        String expected = randomPayload.toString() + randomPayload;

        MessageStream<Message<P>> testSubject = testSubject(List.of(asMessage(randomPayload),
                                                                    asMessage(randomPayload)));

        CompletableFuture<String> result = testSubject.reduce(
                "", (base, message) -> message.getPayload().toString() + message.getPayload().toString()
        );

        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldReturnIdentityWhenReducingEmptyStream() {
        String expected = "42";
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<Message<P>> testSubject = testSubject(List.of());

        CompletableFuture<String> result = testSubject.reduce(
                expected,
                (base, message) -> {
                    invoked.set(true);
                    return message.getPayload().toString() + message.getPayload().toString();
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

        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(), expected);

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, message) -> {
                    invoked.set(true);
                    return message.getPayload().toString() + message.getPayload().toString();
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

        MessageStream<Message<P>> testSubject = failingTestSubject(
                List.of(asMessage(createRandomValidEntry()),
                        asMessage(createRandomValidEntry())),
                expected
        );

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, message) -> {
                    invoked.set(true);
                    return message.getPayload().toString() + message.getPayload().toString();
                }
        );

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnNextHandler_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Message<P> expected = asMessage(createRandomValidEntry());

        MessageStream<Message<P>> testSubject = testSubject(List.of(expected));

        CompletableFuture<Message<P>> result = testSubject.onNextItem((message) -> invoked.set(true))
                                                          .asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnNextHandler_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Message<P> expected = asMessage(createRandomValidEntry());

        MessageStream<Message<P>> testSubject = testSubject(List.of(expected));

        StepVerifier.create(testSubject.onNextItem((message) -> invoked.set(true))
                                       .asFlux())
                    .expectNext(expected)
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldReturnFirstEntryFromOnErrorStream_asCompletableFuture() {
        Exception expectedError = new RuntimeException("oops");
        Message<P> expected = asMessage(createRandomValidEntry());
        MessageStream<Message<P>> onErrorStream = testSubject(List.of(expected));

        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(), expectedError);

        CompletableFuture<Message<P>> result = testSubject.onErrorContinue(error -> {
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
        Message<P> expectedFirst = asMessage(createRandomValidEntry());
        Message<P> expectedSecond = asMessage(createRandomValidEntry());
        MessageStream<Message<P>> onErrorStream = testSubject(List.of(expectedSecond));

        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(expectedFirst), expectedError);

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
        Message<P> expected = asMessage(createRandomValidEntry());
        MessageStream<Message<P>> secondStream = testSubject(List.of());

        MessageStream<Message<P>> firstStream = testSubject(List.of(expected));

        CompletableFuture<Message<P>> result = firstStream.concatWith(secondStream)
                                                          .asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldMoveToConcatWithStream_asCompletableFuture_returnFirstEntryFromSecondStream() {
        Message<P> expected = asMessage(createRandomValidEntry());
        MessageStream<Message<P>> secondStream = testSubject(List.of(expected));

        MessageStream<Message<P>> firstStream = testSubject(List.of());

        CompletableFuture<Message<P>> result = firstStream.concatWith(secondStream)
                                                          .asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void shouldMoveToConcatWithStream_asFlux() {
        Message<P> expectedFirst = asMessage(createRandomValidEntry());
        Message<P> expectedSecond = asMessage(createRandomValidEntry());
        MessageStream<Message<P>> secondStream = testSubject(List.of(expectedSecond));

        MessageStream<Message<P>> testSubject = testSubject(List.of(expectedFirst));

        StepVerifier.create(testSubject.concatWith(secondStream).asFlux())
                    .expectNext(expectedFirst)
                    .expectNext(expectedSecond)
                    .verifyComplete();
    }

    @Test
    void shouldInvokeCompletionCallback_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<Message<P>> testSubject = testSubject(List.of());

        testSubject.whenComplete(() -> invoked.set(true))
                   .asCompletableFuture()
                   .join();

        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(), expected);

        CompletableFuture<Message<P>> result = testSubject.whenComplete(() -> invoked.set(true))
                                                          .asCompletableFuture();
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertFalse(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallback_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<Message<P>> testSubject = testSubject(List.of());

        StepVerifier.create(testSubject.whenComplete(() -> invoked.set(true))
                                       .asFlux())
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<Message<P>> testSubject = failingTestSubject(List.of(), new RuntimeException("oops"));

        StepVerifier.create(testSubject.whenComplete(() -> invoked.set(true))
                                       .asFlux())
                    .verifyError(RuntimeException.class);
        assertFalse(invoked.get());
    }

    @Test
    void shouldResultInFailedStreamWhenCompletionCallbackThrowsAnException_asCompletableFuture() {
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<Message<P>> testSubject = testSubject(List.of(asMessage(createRandomValidEntry())));

        CompletableFuture<Message<P>> result = testSubject.whenComplete(() -> {
                                                              throw expected;
                                                          })
                                                          .asCompletableFuture();

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
    }

    @Test
    void shouldResultInFailedStreamWhenCompletionCallbackThrowsAnException_asFlux() {
        RuntimeException expected = new RuntimeException("oops");
        Message<P> expectedMessage = asMessage(createRandomValidEntry());

        MessageStream<Message<P>> testSubject = testSubject(List.of(expectedMessage));

        StepVerifier.create(testSubject.whenComplete(() -> {
                                           throw expected;
                                       })
                                       .asFlux())
                    .expectNext(expectedMessage)
                    .verifyErrorMatches(expected::equals);
    }
}
