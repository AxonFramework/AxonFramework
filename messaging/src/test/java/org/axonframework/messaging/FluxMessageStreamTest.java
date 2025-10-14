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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link FluxMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class FluxMessageStreamTest extends MessageStreamTest<Message> {

    @Override
    MessageStream<Message> completedTestSubject(List<Message> messages) {
        return FluxUtils.asMessageStream(Flux.fromIterable(messages));
    }

    @Override
    MessageStream.Single<Message> completedSingleStreamTestSubject(Message message) {
        Assumptions.abort("FluxMessageStream doesn't support explicit single-value streams");
        return null;
    }

    @Override
    MessageStream.Empty<Message> completedEmptyStreamTestSubject() {
        Assumptions.abort("FluxMessageStream doesn't support explicitly empty streams");
        return null;
    }


    @Override
    protected MessageStream<Message> uncompletedTestSubject(List<Message> messages,
                                                                    CompletableFuture<Void> completionMarker) {
        return FluxUtils.asMessageStream(Flux.fromIterable(messages).concatWith(
                Flux.create(emitter -> completionMarker.whenComplete(
                        (v, e) -> {
                            if (e != null) {
                                emitter.error(e);
                            } else {
                                emitter.complete();
                            }
                        })))
        );
    }

    @Override
    MessageStream<Message> failingTestSubject(List<Message> messages, Exception failure) {
        return FluxUtils.asMessageStream(Flux.fromIterable(messages).concatWith(Mono.error(failure)));
    }

    @Override
    Message createRandomMessage() {
        return new GenericMessage(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void testCallingCloseReleasesFluxSubscription() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Flux<Message> flux;
        flux = Flux.fromIterable(List.of(createRandomMessage(), createRandomMessage(), createRandomMessage()))
                   .doOnCancel(() -> invoked.set(true));
        MessageStream<Message> testSubject = FluxUtils.asMessageStream(flux);

        assertTrue(testSubject.next().isPresent());
        assertFalse(invoked.get());
        testSubject.close();
        assertTrue(invoked.get());
    }
}