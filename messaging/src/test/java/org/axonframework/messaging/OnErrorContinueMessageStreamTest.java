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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test class validating the {@link OnErrorContinueMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class OnErrorContinueMessageStreamTest extends MessageStreamTest<Message<String>> {

    @Override
    MessageStream<Message<String>> testSubject(List<Message<String>> messages) {
        return new OnErrorContinueMessageStream<>(MessageStream.fromIterable(messages), error -> MessageStream.empty());
    }

    @Override
    MessageStream<Message<String>> failingTestSubject(List<Message<String>> messages,
                                                      Exception failure) {
        return new OnErrorContinueMessageStream<>(MessageStream.fromIterable(messages)
                                                               .concatWith(MessageStream.failed(failure)),
                                                  error -> MessageStream.failed(failure));
    }

    @Override
    Message<String> createRandomMessage() {
        return GenericMessage.asMessage("test-" + ThreadLocalRandom.current().nextInt(10000));
    }
}