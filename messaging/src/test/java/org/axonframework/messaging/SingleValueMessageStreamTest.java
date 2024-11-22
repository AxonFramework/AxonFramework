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

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import static org.axonframework.messaging.QualifiedName.dottedName;

/**
 * Test class validating the {@link SingleValueMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class SingleValueMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> testSubject(List<Message<String>> messages) {
        Assumptions.assumeTrue(messages.size() == 1, "SingleValueMessageStream only supports a single value");
        return MessageStream.just(messages.getFirst());
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages,
                                                      Exception failure) {
        Assumptions.assumeTrue(messages.isEmpty(),
                               "SingleValueMessageStream only supports failures without regular values");
        return MessageStream.fromFuture(CompletableFuture.failedFuture(failure));
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(dottedName("test.message"), "test-" + ThreadLocalRandom.current().nextInt(10000));
    }
}