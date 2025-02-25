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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Test class validating the {@link MessageStream#fromStream(Stream)} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class StreamMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> completedTestSubject(List<Message<String>> messages) {
        return MessageStream.fromStream(messages.stream());
    }

    @Override
    MessageStream.Single<Message<String>> completedSingleStreamTestSubject(Message<String> message) {
        Assumptions.abort("StreamMessageStream doesn't support explicit single-value streams");
        return null;
    }

    @Override
    MessageStream.Empty<Message<String>> completedEmptyStreamTestSubject() {
        Assumptions.abort("StreamMessageStream doesn't support explicitly empty streams");
        return null;
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages,
                                                      Exception failure) {
        Assumptions.abort("StreamMessageStream doesn't support failures");
        return null;
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }
}