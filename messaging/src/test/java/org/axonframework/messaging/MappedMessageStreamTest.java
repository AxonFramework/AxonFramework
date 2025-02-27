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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Test class validating the {@link MappedMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class MappedMessageStreamTest extends MessageStreamTest<Message<String>> {

    private static final Function<Entry<Message<String>>, Entry<Message<String>>> NO_OP_MAPPER = entry -> entry;

    @Override
    MessageStream<Message<String>> completedTestSubject(List<Message<String>> messages) {
        if (messages.size() == 1) {
            return new MappedMessageStream.Single<>(MessageStream.just(messages.getFirst()), NO_OP_MAPPER);
        }
        return new MappedMessageStream<>(MessageStream.fromIterable(messages), NO_OP_MAPPER);
    }

    @Override
    MessageStream.Single<Message<String>> completedSingleStreamTestSubject(Message<String> message) {
        return new MappedMessageStream.Single<>(MessageStream.just(message), NO_OP_MAPPER);
    }

    @Override
    MessageStream.Empty<Message<String>> completedEmptyStreamTestSubject() {
        Assumptions.abort("MappedMessageStream does not support empty streams");
        return null;
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> entries, Exception failure) {
        return new MappedMessageStream<>(MessageStream.fromIterable(entries)
                                                      .concatWith(MessageStream.failed(failure)), NO_OP_MAPPER);
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }
}