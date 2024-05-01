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

import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public abstract class MessageStreamTest<V> {

    abstract MessageStream<Message<V>> createTestSubject(List<Message<V>> values);

    abstract MessageStream<Message<V>> createTestSubject(List<Message<V>> values, Exception failure);

    abstract V createRandomValidStreamEntry();

    @Test
    void shouldMapSingleValue_Future() {
        Message<V> in = GenericMessage.asMessage(createRandomValidStreamEntry());
        Message<V> out = GenericMessage.asMessage(createRandomValidStreamEntry());

        MessageStream<Message<V>> testSubject = createTestSubject(List.of(in));
        var actual = testSubject.map(input -> out).asCompletableFuture().join();
        assertSame(out, actual);
    }

    @Test
    void shouldMapSingleValue_Flux() {
        Message<V> in = GenericMessage.asMessage(createRandomValidStreamEntry());
        Message<V> out = GenericMessage.asMessage(createRandomValidStreamEntry());

        MessageStream<Message<V>> testSubject = createTestSubject(List.of(in));
        StepVerifier.create(testSubject.map(input -> out).asFlux())
                    .expectNext(out)
                    .verifyComplete();
    }

    @Test
    void shouldMapMultipleValues_Flux() {
        Message<V> in1 = GenericMessage.asMessage(createRandomValidStreamEntry());
        Message<V> out1 = GenericMessage.asMessage(createRandomValidStreamEntry());
        Message<V> in2 = GenericMessage.asMessage(createRandomValidStreamEntry());
        Message<V> out2 = GenericMessage.asMessage(createRandomValidStreamEntry());

        MessageStream<Message<V>> testSubject = createTestSubject(List.of(in1, in2));
        StepVerifier.create(testSubject.map(input -> input == in1 ? out1 : out2).asFlux())
                    .expectNext(out1, out2)
                    .verifyComplete();
    }

    @Test
    void shouldMapValuesUntilFailure_Flux() {
        Message<V> in = GenericMessage.asMessage(createRandomValidStreamEntry());
        Message<V> out = GenericMessage.asMessage(createRandomValidStreamEntry());

        MessageStream<Message<V>> testSubject = createTestSubject(List.of(in), new MockException())
                .map(input -> out)
                .onErrorContinue(MessageStream::failed);

        StepVerifier.create(testSubject.asFlux())
                    .expectNextMatches(out::equals)
                    .expectErrorMatches(MockException.class::isInstance)
                    .verify();
    }

    @Test
    void shouldNotCallMapperForEmptyStream_CompletableFuture() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<?> testSubject = createTestSubject(List.of()).map(i -> {
            invoked.set(true);
            return i;
        });
        Object actual = testSubject.asCompletableFuture().join();
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
        assertNull(actual, "Expected null value from empty stream");
    }

    @Test
    void shouldCompleteWithNullOnEmptyList() {
        MessageStream<Message<V>> testSubject = createTestSubject(Collections.emptyList());
        CompletableFuture<Message<V>> actual = testSubject.asCompletableFuture();

        assertNull(actual.resultNow());
    }

    @Test
    void shouldNotCallMapperForEmptyStream_Flux() {
        AtomicBoolean invoked = new AtomicBoolean();

        MessageStream<?> testSubject = createTestSubject(List.of()).map(i -> {
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

        MessageStream<?> testSubject = createTestSubject(List.of(), new MockException()).map(i -> {
            invoked.set(true);
            return i;
        });
        assertTrue(testSubject.asCompletableFuture().isCompletedExceptionally());
        assertFalse(invoked.get(), "Mapper function should not be invoked for empty streams");
    }

    @Test
    void shouldEmitOriginalExceptionAsFailure() {
        MessageStream<?> testSubject = createTestSubject(List.of(), new MockException());

        CompletableFuture<?> actual = testSubject.asCompletableFuture();
        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }
}
