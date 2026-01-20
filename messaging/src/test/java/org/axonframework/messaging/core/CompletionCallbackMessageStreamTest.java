/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test class validating the {@link CompletionCallbackMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class CompletionCallbackMessageStreamTest extends MessageStreamTest<Message> {

    private static final Runnable NO_OP_COMPLETION_CALLBACK = () -> {
    };

    @Override
    protected MessageStream<Message> completedTestSubject(List<Message> messages) {
        return new CompletionCallbackMessageStream<>(MessageStream.fromIterable(messages), NO_OP_COMPLETION_CALLBACK);
    }

    @Override
    protected MessageStream.Single<Message> completedSingleStreamTestSubject(Message message) {
        Assumptions.abort("CompletionCallbackMessageStream does not support explicit single-item streams");
        return null;
    }

    @Override
    protected MessageStream.Empty<Message> completedEmptyStreamTestSubject() {
        Assumptions.abort("CompletionCallbackMessageStream does not support explicit zero-item streams");
        return null;
    }

    @Override
    protected MessageStream<Message> failingTestSubject(List<Message> messages,
                                                        RuntimeException failure) {
        return new CompletionCallbackMessageStream<>(MessageStream.fromIterable(messages)
                                                                  .concatWith(MessageStream.failed(failure)),
                                                     NO_OP_COMPLETION_CALLBACK);
    }

    @Override
    protected Message createRandomMessage() {
        return new GenericMessage(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }
}