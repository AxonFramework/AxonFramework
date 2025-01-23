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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link FluxMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class FluxMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> completedTestSubject(List<Message<String>> messages) {
        return MessageStream.fromFlux(Flux.fromIterable(messages));
    }

    @Override
    protected MessageStream<Message<String>> uncompletedTestSubject(List<Message<String>> messages) {
        return MessageStream.fromFlux(Flux.fromIterable(messages).concatWith(Flux.create(emitter -> {
            // does nothing to keep it lingering
        })));
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages, Exception failure) {
        return MessageStream.fromFlux(Flux.fromIterable(messages).concatWith(Mono.error(failure)));
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void testCallingCloseReleasesFluxSubscription() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Flux<Message<String>> flux;
        flux = Flux.fromIterable(List.of(createRandomMessage(), createRandomMessage(), createRandomMessage()))
                   .doOnCancel(() -> invoked.set(true));
        MessageStream<Message<String>> testSubject = MessageStream.fromFlux(flux);

        assertTrue(testSubject.next().isPresent());
        assertFalse(invoked.get());
        testSubject.close();
        assertTrue(invoked.get());
    }
}