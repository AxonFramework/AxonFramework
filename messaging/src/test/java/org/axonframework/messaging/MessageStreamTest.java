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

import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite used to validate implementations of the {@link MessageStream}.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of the {@link MessageStream stream}
 *            under test.
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public abstract class MessageStreamTest<M extends Message> {

    /**
     * Construct a test subject using the given {@code messages} as the source.
     * <p>
     * It is the task of the implementer of this method to map the {@code messages} to {@link Entry entries} for the
     * {@link MessageStream stream} under test.
     *
     * @param messages The {@link Message Message} of type {@code M} acting as the source for the
     *                 {@link MessageStream stream} under construction.
     * @return A {@link MessageStream stream} to use for testing.
     */
    abstract MessageStream<M> completedTestSubject(List<M> messages);

    /**
     * Construct a test subject with a {@link MessageStream.Single single} entry using the given {@code message} as the
     * source.
     * <p>
     * It is the task of the implementer of this method to map th {@code messages} to an {@link Entry} for the
     * {@link MessageStream.Single stream} under test.
     *
     * @param message The {@link Message} of type {@code M} acting as the source for the
     *                {@link MessageStream.Single single-entry-stream} under construction.
     * @return A {@link MessageStream.Single single-entry-stream} to use for testing.
     */
    abstract MessageStream.Single<M> completedSingleStreamTestSubject(M message);

    /**
     * Construct an {@link MessageStream.Empty empty} test subject.
     *
     * @return An {@link MessageStream.Empty empty stream} to use for testing.
     */
    abstract MessageStream.Empty<M> completedEmptyStreamTestSubject();

    /**
     * Construct a test subject using the given {@code messages} as the source. The resulting stream must not report as
     * completed if given messages are consumed until the given {@code completionMarker} completes.
     * <p>
     * It is the task of the implementer of this method to map the {@code messages} to {@link Entry entries} for the
     * {@link MessageStream stream} under test.
     *
     * @param messages The {@link Message Message} of type {@code M} acting as the source for the
     *                 {@link MessageStream stream} under construction.
     * @return A {@link MessageStream stream} to use for testing.
     */
    protected MessageStream<M> uncompletedTestSubject(List<M> messages, CompletableFuture<Void> completionMarker) {
        return completedTestSubject(messages)
                .concatWith(
                        DelayedMessageStream.create(completionMarker.thenApply(e -> MessageStream.empty())
                                                                    .exceptionally(MessageStream::failed))
                                            .cast()
                );
    }

    /**
     * Construct a test subject using the given {@code messages} as the source, which will fail due to the given
     * {@code failure}.
     * <p>
     * It is the task of the implementer of this method to map the {@code messages} to {@link Entry entries} for the
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
    void shouldInvokeOnAvailableCallbackWhenCompletedExceptionally() {
        MessageStream<M> testSubject = failingTestSubject(List.of(), new RuntimeException("Oops"));

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void shouldReturnEmptyNextAndNoAvailableMessagesOnError() {
        MessageStream<M> testSubject = failingTestSubject(List.of(), new RuntimeException("Oops"));

        assertFalse(testSubject.hasNextAvailable());
        assertFalse(testSubject.next().isPresent());
        assertTrue(testSubject.isCompleted());
    }

    @Test
    void shouldNotInvokeOnAvailableCallbackUntilCompleted() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(), new CompletableFuture<>());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallbackWhenNextIsRequestedAfterCompletion() {
        CompletableFuture<Void> completionMarker = new CompletableFuture<>();
        AtomicBoolean invoked = new AtomicBoolean(false);
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(), completionMarker)
                .whenComplete(() -> invoked.set(true));


        while (testSubject.next().isPresent()) {
            assertFalse(invoked.get());
        }

        completionMarker.complete(null);

        assertFalse(testSubject.next().isPresent());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallbackWhenOnAvailableIsRequestedAfterCompletion() {
        CompletableFuture<Void> completionMarker = new CompletableFuture<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        MessageStream<M> testSubject = uncompletedTestSubject(List.of(), completionMarker)
                .whenComplete(() -> invoked.set(true));

        while (testSubject.hasNextAvailable()) {
            assertFalse(invoked.get());
            testSubject.next();
        }

        completionMarker.complete(null);
        // streams _may_ notify their listeners at this point

        assertFalse(testSubject.hasNextAvailable());

        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallbackOnceAllMessagesHaveBeenConsumed() {
        CompletableFuture<Void> completionMarker = new CompletableFuture<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        MessageStream<M> testSubject = uncompletedTestSubject(List.of(createRandomMessage()), completionMarker)
                .whenComplete(() -> invoked.set(true));

        completionMarker.complete(null);
        // streams _must not_ have notified their listeners at this point
        assertFalse(invoked.get());

        testSubject.next();
        // consuming the last message must not trigger the completion callback. The next interaction with the stream will.

        assertFalse(invoked.get());

        assertFalse(testSubject.hasNextAvailable());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallbackImmediatelyOnCompletedEmptyStream() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        completedEmptyStreamTestSubject().whenComplete(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallbackWhenStreamCompletesEmpty() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        CompletableFuture<Void> completionMarker = new CompletableFuture<>();
        uncompletedTestSubject(List.of(), completionMarker).whenComplete(() -> invoked.set(true));

        assertFalse(invoked.get());
        completionMarker.complete(null);
        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeOnAvailableCallbackWhenNotCompletedAndAvailableMessageIsConsumed() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(createRandomMessage()),
                                                              new CompletableFuture<>());

        assertTrue(testSubject.next().isPresent());
        assertFalse(testSubject.next().isPresent());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
    }

    @Test
    void shouldNotInvokeOnAvailableCallbackWhenNotCompletedAndAvailableMessagesAreConsumed() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(createRandomMessage(), createRandomMessage()),
                                                              new CompletableFuture<>());

        assertTrue(testSubject.next().isPresent());
        assertTrue(testSubject.next().isPresent());
        assertFalse(testSubject.next().isPresent());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
    }

    @Test
    void shouldInvokeOnAvailableCallbackWhenNotCompletedAndNotAllAvailableMessagesAreConsumed() {
        MessageStream<M> testSubject = uncompletedTestSubject(List.of(createRandomMessage(), createRandomMessage()),
                                                              new CompletableFuture<>());

        assertTrue(testSubject.next().isPresent());

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onAvailable(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void shouldEmitOriginalExceptionAsFailure() {
        MessageStream<M> testSubject = failingTestSubject(List.of(), new MockException());

        CompletableFuture<Entry<M>> actual = testSubject.first().asCompletableFuture();

        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }

    @Test
    void shouldCompleteWithNullOnEmptyList() {
        MessageStream<M> testSubject = completedTestSubject(Collections.emptyList());

        CompletableFuture<Entry<M>> actual = testSubject.first().asCompletableFuture();

        assertNull(actual.resultNow());
    }

    @Test
    void shouldCompleteWithNullOnEmptyStream() {
        MessageStream.Empty<M> testSubject = completedEmptyStreamTestSubject();

        CompletableFuture<Entry<M>> actual = testSubject.first().asCompletableFuture();

        assertNull(actual.resultNow());
    }

    @Test
    void shouldMapSingleEntry_asCompletableFuture() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in));

        var actual = testSubject.map(entry -> entry.map(input -> out))
                                .first().asCompletableFuture()
                                .join()
                                .message();

        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleEntry_asCompletableFuture_ExplicitlyDeclaredSingle() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream.Single<M> testSubject = completedSingleStreamTestSubject(in);

        var actual = testSubject.map(entry -> entry.map(input -> out))
                                .first().asCompletableFuture()
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
                                .first().asCompletableFuture()
                                .join()
                                .message();

        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleMessage_asCompletableFuture_ExplicitlyDeclaredSingle() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream.Single<M> testSubject = completedSingleStreamTestSubject(in);

        var actual = testSubject.mapMessage(input -> out)
                                .first().asCompletableFuture()
                                .join()
                                .message();

        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleEntry_asFlux() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in));

        StepVerifier.create(FluxUtils.of(testSubject.map(entry -> entry.map(input -> out))))
                    .expectNextMatches(entry -> entry.message().equals(out))
                    .verifyComplete();
    }

    @Test
    void shouldMapSingleEntry_asMono() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream.Single<M> testSubject = completedSingleStreamTestSubject(in);

        StepVerifier.create(FluxUtils.of(testSubject.map(entry -> entry.map(input -> out))).singleOrEmpty())
                    .expectNextMatches(entry -> entry.message().equals(out))
                    .verifyComplete();
    }

    @Test
    void shouldMapSingleMessage_asFlux() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(in));

        StepVerifier.create(FluxUtils.of(testSubject.mapMessage(input -> out)))
                    .expectNextMatches(entry -> entry.message().equals(out))
                    .verifyComplete();
    }

    @Test
    void shouldMapSingleMessage_asMono() {
        M in = createRandomMessage();
        M out = createRandomMessage();

        MessageStream.Single<M> testSubject = completedSingleStreamTestSubject(in);

        StepVerifier.create(FluxUtils.of(testSubject.mapMessage(input -> out)).singleOrEmpty())
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

        StepVerifier.create(FluxUtils.of(testSubject.map(entry -> entry.map(input -> input == in1 ? out1 : out2))))
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

        StepVerifier.create(FluxUtils.of(testSubject.mapMessage(input -> input == in1 ? out1 : out2)))
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

        StepVerifier.create(FluxUtils.of(testSubject))
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

        StepVerifier.create(FluxUtils.of(testSubject))
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

        Entry<M> actual = testSubject.first().asCompletableFuture().join();

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

        Entry<M> actual = testSubject.first().asCompletableFuture().join();

        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
        assertNull(actual, "Expected null value from empty stream");
    }

    @Test
    void shouldNotCallMapperForEmptyStream_asCompletableFuture_ExplicitlyDeclaredEmpty() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream.Empty<M> testSubject = completedEmptyStreamTestSubject()
                .map(entry -> {
                    invoked.set(true);
                    return entry;
                });

        Entry<M> actual = testSubject.first().asCompletableFuture().join();

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

        StepVerifier.create(FluxUtils.of(testSubject))
                    .verifyComplete();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMapperForEmptyStream_asFlux_ExplicitlyDeclaredEmpty() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream.Empty<M> testSubject = completedEmptyStreamTestSubject()
                .map(entry -> {
                    invoked.set(true);
                    return entry;
                });

        StepVerifier.create(FluxUtils.of(testSubject))
                    .verifyComplete();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMapperForEmptyStream_asMono_ExplicitlyDeclaredEmpty() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream.Empty<M> testSubject = completedEmptyStreamTestSubject()
                .map(entry -> {
                    invoked.set(true);
                    return entry;
                });

        StepVerifier.create(FluxUtils.of(testSubject).singleOrEmpty())
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

        StepVerifier.create(FluxUtils.of(testSubject))
                    .verifyComplete();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMessageMapperForEmptyStream_asFlux_ExplicitlyDeclaredEmpty() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream.Empty<M> testSubject = completedEmptyStreamTestSubject()
                .mapMessage(message -> {
                    invoked.set(true);
                    return message;
                });

        StepVerifier.create(FluxUtils.of(testSubject))
                    .verifyComplete();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldNotCallMessageMapperForEmptyStream_asMono_ExplicitlyDeclaredEmpty() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream.Empty<M> testSubject = completedEmptyStreamTestSubject()
                .mapMessage(message -> {
                    invoked.set(true);
                    return message;
                });

        StepVerifier.create(FluxUtils.of(testSubject).singleOrEmpty())
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

        assertTrue(testSubject.first().asCompletableFuture().isCompletedExceptionally());
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

        assertTrue(testSubject.first().asCompletableFuture().isCompletedExceptionally());
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldReduceToExpectedResult() {
        M randomMessage = createRandomMessage();
        String expected = randomMessage.payload().toString() + randomMessage.payload().toString();

        MessageStream<M> testSubject = completedTestSubject(List.of(randomMessage, randomMessage));

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> entry.message().payload().toString() + entry.message().payload().toString()
        );

        assertTrue(result.isDone());
        assertEquals(expected, result.join());
    }

    @Test
    void errorInReduceFunctionLeadsToFailedStream() {
        AtomicBoolean invoked = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<M> testSubject = completedTestSubject(List.of(createRandomMessage(), createRandomMessage()));

        CompletableFuture<String> result = testSubject.reduce(
                "",
                (base, entry) -> {
                    invoked.set(true);
                    throw expected;
                }
        );

        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertTrue(invoked.get());
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
                    return entry.message().payload().toString() + entry.message().payload().toString();
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
                    return entry.message().payload().toString() + entry.message().payload().toString();
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
                    return entry.message().payload().toString() + entry.message().payload().toString();
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
                                                        .first().asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join().message());
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeOnNextHandler_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        M expected = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(expected));

        StepVerifier.create(FluxUtils.of(testSubject.onNext(entry -> invoked.set(true))))
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
                           .first().asCompletableFuture();

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

        StepVerifier.create(
            FluxUtils.of(testSubject.onErrorContinue(error -> {
                assertEquals(expectedError, FutureUtils.unwrap(error));
                return onErrorStream;
            })
        ))
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
                                                        .first().asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join().message());
    }

    @Test
    void shouldMoveToConcatWithStream_asCompletableFuture_returnFirstEntryFromSecondStream() {
        M expected = createRandomMessage();
        MessageStream<M> secondStream = completedTestSubject(List.of(expected));

        MessageStream<M> firstStream = completedTestSubject(List.of());

        CompletableFuture<Entry<M>> result = firstStream.concatWith(secondStream)
                                                        .first().asCompletableFuture();
        assertTrue(result.isDone());
        assertEquals(expected, result.join().message());
    }

    @Test
    void shouldMoveToConcatWithStream_asFlux() {
        M expectedFirst = createRandomMessage();
        M expectedSecond = createRandomMessage();
        MessageStream<M> secondStream = completedTestSubject(List.of(expectedSecond));

        MessageStream<M> testSubject = completedTestSubject(List.of(expectedFirst));

        StepVerifier.create(FluxUtils.of(testSubject.concatWith(secondStream)))
                    .expectNextMatches(entry -> entry.message().equals(expectedFirst))
                    .expectNextMatches(entry -> entry.message().equals(expectedSecond))
                    .verifyComplete();
    }

    @Test
    void shouldConcatWithMultipleStreamsSequentially() {
        M first = createRandomMessage();
        M second = createRandomMessage();
        M third = createRandomMessage();

        MessageStream<M> stream1 = completedTestSubject(List.of(first));
        MessageStream<M> stream2 = completedTestSubject(List.of(second));
        MessageStream<M> stream3 = completedTestSubject(List.of(third));

        MessageStream<M> concatenated = stream1.concatWith(stream2).concatWith(stream3);

        StepVerifier.create(FluxUtils.of(concatenated))
                    .expectNextMatches(entry -> entry.message().equals(first))
                    .expectNextMatches(entry -> entry.message().equals(second))
                    .expectNextMatches(entry -> entry.message().equals(third))
                    .verifyComplete();
    }

    @Test
    void shouldFailConcatWithIfFirstStreamFails() {
        RuntimeException testException = new RuntimeException("First stream failed");
        MessageStream<M> firstStream = failingTestSubject(List.of(), testException);
        MessageStream<M> secondStream = completedTestSubject(List.of(createRandomMessage()));

        MessageStream<M> concatenated = firstStream.concatWith(secondStream);

        StepVerifier.create(FluxUtils.of(concatenated))
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("First stream failed"))
                    .verify();
    }

    @Test
    void shouldFailConcatWithIfSecondStreamFails() {
        RuntimeException testException = new RuntimeException("Second stream failed");
        M firstMessage = createRandomMessage();
        MessageStream<M> firstStream = completedTestSubject(List.of(firstMessage));
        MessageStream<M> secondStream = failingTestSubject(List.of(), testException);

        MessageStream<M> concatenated = firstStream.concatWith(secondStream);

        StepVerifier.create(FluxUtils.of(concatenated))
                    .expectNextMatches(entry -> entry.message().equals(firstMessage))
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("Second stream failed"))
                    .verify();
    }

    @Test
    void shouldPreserveOrderInConcatWithStreams() {
        M first1 = createRandomMessage();
        M first2 = createRandomMessage();
        M second1 = createRandomMessage();
        M second2 = createRandomMessage();

        MessageStream<M> stream1 = completedTestSubject(List.of(first1, first2));
        MessageStream<M> stream2 = completedTestSubject(List.of(second1, second2));

        MessageStream<M> concatenated = stream1.concatWith(stream2);

        StepVerifier.create(FluxUtils.of(concatenated))
                    .expectNextMatches(entry -> entry.message().equals(first1))
                    .expectNextMatches(entry -> entry.message().equals(first2))
                    .expectNextMatches(entry -> entry.message().equals(second1))
                    .expectNextMatches(entry -> entry.message().equals(second2))
                    .verifyComplete();
    }

    @Test
    void shouldInvokeCompletionCallback_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of());

        testSubject.whenComplete(() -> invoked.set(true))
                   .first().asCompletableFuture()
                   .join();

        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asCompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("oops");

        MessageStream<M> testSubject = failingTestSubject(List.of(), expected);

        CompletableFuture<Entry<M>> result = testSubject.whenComplete(() -> invoked.set(true))
                                                        .first().asCompletableFuture();
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected, result.exceptionNow());
        assertFalse(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallback_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = completedTestSubject(List.of());

        StepVerifier.create(FluxUtils.of(testSubject.whenComplete(() -> invoked.set(true))))
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldInvokeCompletionCallback_asMono() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream.Single<M> testSubject = completedSingleStreamTestSubject(createRandomMessage());

        StepVerifier.create(FluxUtils.of(testSubject.whenComplete(() -> invoked.set(true))).singleOrEmpty())
                    .expectNextCount(1)
                    .verifyComplete();
        assertTrue(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asFlux() {
        AtomicBoolean invoked = new AtomicBoolean();

        RuntimeException oops = new RuntimeException("oops");
        MessageStream<M> testSubject = failingTestSubject(List.of(), oops);

        StepVerifier.create(FluxUtils.of(testSubject.whenComplete(() -> invoked.set(true))))
                    .verifyErrorMatches(e -> e == oops);
        assertFalse(invoked.get());
    }

    @Test
    void shouldNotInvokeCompletionCallbackForFailedStream_asMono() {
        AtomicBoolean invoked = new AtomicBoolean();

        RuntimeException oops = new RuntimeException("oops");
        MessageStream<M> testSubject = failingTestSubject(List.of(), oops);
        Assumptions.assumeTrue(testSubject instanceof MessageStream.Single<M>);

        StepVerifier.create(
            FluxUtils.of(((MessageStream.Single<M>) testSubject).whenComplete(() -> invoked.set(true)))
                .singleOrEmpty()
        )
        .verifyErrorMatches(e -> e == oops);

        assertFalse(invoked.get());
    }

    @Test
    void shouldResultInFailedStreamWhenCompletionCallbackThrowsAnException_asFlux() {
        RuntimeException expected = new RuntimeException("oops");
        M expectedMessage = createRandomMessage();

        MessageStream<M> testSubject = completedTestSubject(List.of(expectedMessage));

        StepVerifier.create(
            FluxUtils.of(testSubject.whenComplete(() -> {
                throw expected;
            }))
        )
        .expectNextMatches(entry -> entry.message().equals(expectedMessage))
        .verifyErrorMatches(expected::equals);
    }

    @Test
    void shouldExecuteWhenCompleteCallbackOnlyAfterAllMessagesProcessed() {
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);
        AtomicInteger processedCount = new AtomicInteger(0);

        List<M> messages = List.of(createRandomMessage(), createRandomMessage(), createRandomMessage());
        MessageStream<M> testSubject = completedTestSubject(messages)
            .onNext(entry -> {
                processedCount.incrementAndGet();
                assertFalse(callbackExecuted.get(), "Callback should not execute until completion");
            })
            .whenComplete(() -> callbackExecuted.set(true));

        StepVerifier.create(FluxUtils.of(testSubject))
                    .expectNextCount(3)
                    .verifyComplete();

        assertEquals(3, processedCount.get());
        assertTrue(callbackExecuted.get());
    }

    @Test
    void shouldNotExecuteWhenCompleteCallbackOnError() {
        RuntimeException testException = new RuntimeException("Stream failed");
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);

        MessageStream<M> testSubject = failingTestSubject(List.of(), testException)
            .whenComplete(() -> callbackExecuted.set(true));

        StepVerifier.create(FluxUtils.of(testSubject))
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("Stream failed"))
                    .verify();

        assertFalse(callbackExecuted.get());
    }

    @Test
    void shouldNotChangeStreamContentWithWhenComplete() {
        List<M> originalMessages = List.of(createRandomMessage(), createRandomMessage(), createRandomMessage());
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);

        MessageStream<M> original = completedTestSubject(originalMessages);
        MessageStream<M> withCallback = original.whenComplete(() -> callbackExecuted.set(true));

        StepVerifier.create(FluxUtils.of(withCallback))
                    .expectNextMatches(entry -> entry.message().equals(originalMessages.get(0)))
                    .expectNextMatches(entry -> entry.message().equals(originalMessages.get(1)))
                    .expectNextMatches(entry -> entry.message().equals(originalMessages.get(2)))
                    .verifyComplete();

        assertTrue(callbackExecuted.get());
    }

    @Test
    void shouldChainMultipleWhenCompleteCallbacks() {
        AtomicInteger callbackCount = new AtomicInteger(0);

        MessageStream<M> testSubject = completedTestSubject(List.of(createRandomMessage()))
            .whenComplete(callbackCount::incrementAndGet)
            .whenComplete(callbackCount::incrementAndGet)
            .whenComplete(callbackCount::incrementAndGet);

        StepVerifier.create(FluxUtils.of(testSubject))
                    .expectNextCount(1)
                    .verifyComplete();

        assertEquals(3, callbackCount.get());
    }

    @Test
    void shouldShowDifferentPurposesOfConcatWithAndWhenComplete() {
        AtomicBoolean stream1Completed = new AtomicBoolean(false);
        AtomicBoolean stream2Completed = new AtomicBoolean(false);

        MessageStream<M> stream1 = completedTestSubject(List.of(createRandomMessage()))
            .whenComplete(() -> stream1Completed.set(true));

        MessageStream<M> stream2 = completedTestSubject(List.of(createRandomMessage()))
            .whenComplete(() -> stream2Completed.set(true));

        MessageStream<M> concatenated = stream1.concatWith(stream2);

        StepVerifier.create(FluxUtils.of(concatenated))
                    .expectNextCount(2)
                    .verifyComplete();

        assertTrue(stream1Completed.get());
        assertTrue(stream2Completed.get());
    }

    @Test
    void shouldInvokeCallbackOnceAdditionalMessagesBecomeAvailable() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<M> testSubject = uncompletedTestSubject(List.of(), new CompletableFuture<>());
        testSubject.onAvailable(() -> invoked.set(true));

        assertFalse(invoked.get());
        publishAdditionalMessage(testSubject, createRandomMessage());
        assertTrue(invoked.get());
    }

    @Test
    void shouldCallCloseWhenConsumingOnlyTheFirstMessage() {
        //noinspection unchecked
        MessageStream<M> mock = mock();
        MessageStream<M> testSubject = completedTestSubject(List.of(createRandomMessage(),
                                                                    createRandomMessage())).concatWith(mock);
        MessageStream.Single<M> first = testSubject.first();
        assertTrue(first.next().isPresent());
        assertFalse(first.hasNextAvailable());
        assertFalse(first.error().isPresent());
        assertFalse(first.next().isPresent());
        assertFalse(first.hasNextAvailable());
        assertFalse(first.next().isPresent());
        assertTrue(first.isCompleted());

        verify(mock).close();
    }

    @Test
    void shouldCallCloseWhenConsumingFirstAsCompletableFuture() {
        //noinspection unchecked
        MessageStream<M> mock = mock();
        MessageStream<M> testSubject = completedTestSubject(List.of(createRandomMessage(),
                                                                    createRandomMessage()))
                .concatWith(mock);
        CompletableFuture<Entry<M>> first = testSubject.first().asCompletableFuture();
        assertTrue(first.isDone());
        assertNotNull(first.getNow(null));

        verify(mock).close();
    }

    @Nested
    class Filter {

        @Test
        void filterKeepsEntriesForWhichTrueIsReturned() {
            M firstMessage = createRandomMessage();
            MessageStream<M> testSubject = completedTestSubject(List.of(firstMessage, createRandomMessage()));

            MessageStream<M> result = testSubject.filter(entry -> entry.message().equals(firstMessage));

            Optional<Entry<M>> next = result.next();
            assertTrue(next.isPresent());
            assertEquals(firstMessage, next.get().message());
            assertFalse(result.next().isPresent());
            assertTrue(result.isCompleted());
        }

        @Test
        void filterRemovesEntriesForWhichFalseIsReturned() {
            M firstMessage = createRandomMessage();
            M secondMessage = createRandomMessage();
            MessageStream<M> testSubject = completedTestSubject(List.of(firstMessage, secondMessage));

            MessageStream<M> result = testSubject.filter(entry -> !entry.message().equals(secondMessage));

            Optional<Entry<M>> next = result.next();
            assertTrue(next.isPresent());
            assertEquals(firstMessage, next.get().message());
            assertFalse(result.next().isPresent());
            assertTrue(result.isCompleted());
        }
    }

    @Nested
    public class Peek {

        @Test
        void shouldReturnNextEntryWithoutAdvancing() {
            //given
            M message = createRandomMessage();
            MessageStream<M> stream = completedTestSubject(List.of(message));

            //when
            Optional<Entry<M>> peeked = stream.peek();
            Optional<Entry<M>> peekedAgain = stream.peek();

            //then
            assertTrue(peeked.isPresent());
            assertEquals(message.payload(), peeked.get().message().payload());
            assertTrue(peekedAgain.isPresent());
            assertEquals(message.payload(), peekedAgain.get().message().payload());
            assertFalse(stream.isCompleted());
            assertTrue(stream.hasNextAvailable());
        }

        @Test
        void shouldReturnEmptyOnEmptyStream() {
            //given
            MessageStream<M> stream = completedTestSubject(List.of());

            //when
            Optional<Entry<M>> peeked = stream.peek();

            //then
            assertTrue(peeked.isEmpty());
        }

        @Test
        void shouldNotAdvanceStream() {
            //given
            List<M> messages = List.of(createRandomMessage(), createRandomMessage());
            MessageStream<M> stream = completedTestSubject(messages);

            //when
            Optional<Entry<M>> peeked = stream.peek();
            Optional<Entry<M>> next = stream.next();

            //then
            assertTrue(peeked.isPresent());
            assertEquals(messages.getFirst().payload(), peeked.get().message().payload());
            assertTrue(next.isPresent());
            assertEquals(messages.getFirst().payload(), next.get().message().payload());
        }

        @Test
        void followedByNextReturnsSameEntry() {
            //given
            M message = createRandomMessage();
            MessageStream<M> stream = completedTestSubject(List.of(message));

            //when
            Optional<Entry<M>> peeked = stream.peek();
            Optional<Entry<M>> next = stream.next();

            //then
            assertTrue(peeked.isPresent());
            assertTrue(next.isPresent());
            assertEquals(peeked.get().message().payload(), next.get().message().payload());
        }

        @Test
        void returnsEmptyAfterConsumingAll() {
            //given
            List<M> messages = List.of(createRandomMessage(), createRandomMessage());
            MessageStream<M> stream = completedTestSubject(messages);

            //when
            stream.next();
            stream.next();
            Optional<Entry<M>> peeked = stream.peek();

            //then
            assertTrue(peeked.isEmpty());
        }

        @Test
        void returnsEmptyOnEmptyStreamType() {
            //given
            MessageStream.Empty<M> stream = completedEmptyStreamTestSubject();

            //when
            Optional<Entry<M>> peeked = stream.peek();

            //then
            assertTrue(peeked.isEmpty());
        }

        @Test
        void returnsEmptyOnFailedStream() {
            //given
            MessageStream<M> stream = failingTestSubject(List.of(), new RuntimeException("fail"));

            //when
            Optional<Entry<M>> peeked = stream.peek();

            //then
            assertTrue(peeked.isEmpty());
        }

        @Test
        void onSingleStreamReturnsEntryWithoutAdvancing() {
            //given
            M message = createRandomMessage();
            MessageStream.Single<M> stream = completedSingleStreamTestSubject(message);

            //when
            Optional<Entry<M>> peeked = stream.peek();
            Optional<Entry<M>> peekedAgain = stream.peek();

            //then
            assertTrue(peeked.isPresent());
            assertEquals(message.payload(), peeked.get().message().payload());
            assertTrue(peekedAgain.isPresent());
            assertEquals(message.payload(), peekedAgain.get().message().payload());
            assertFalse(stream.isCompleted());
            assertTrue(stream.hasNextAvailable());
        }

        @Test
        void onSingleStreamReturnsEmptyAfterNext() {
            //given
            M message = createRandomMessage();
            MessageStream.Single<M> stream = completedSingleStreamTestSubject(message);

            //when
            stream.next();
            Optional<Entry<M>> peeked = stream.peek();

            //then
            assertTrue(peeked.isEmpty());
            assertTrue(stream.isCompleted());
            assertFalse(stream.hasNextAvailable());
        }
    }
}
