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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test class validating the {@link ConcatenatingMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class ConcatenatingMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> completedTestSubject(List<Message<String>> messages) {
        if (messages.isEmpty()) {
            return new ConcatenatingMessageStream<>(MessageStream.emptyOfType(), MessageStream.emptyOfType());
        } else if (messages.size() == 1) {
            return new ConcatenatingMessageStream<>(MessageStream.just(messages.getFirst()), MessageStream.emptyOfType());
        }
        return new ConcatenatingMessageStream<>(
                MessageStream.just(messages.getFirst()),
                MessageStream.fromIterable(messages.subList(1, messages.size()))
        );
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages,
                                                      Exception failure) {
        return completedTestSubject(messages).concatWith(MessageStream.failed(failure));
    }

    @Override
    Message<String> createRandomMessage() {
        return new GenericMessage<>(new MessageType("message"),
                                    "test-" + ThreadLocalRandom.current().nextInt(10000));
    }
}