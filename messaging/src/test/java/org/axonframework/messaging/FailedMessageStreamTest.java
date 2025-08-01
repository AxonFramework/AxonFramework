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

/**
 * Test class validating the {@link FailedMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class FailedMessageStreamTest extends MessageStreamTest<Message<Void>> {

    @Override
    MessageStream<Message<Void>> completedTestSubject(List<Message<Void>> messages) {
        Assumptions.abort("FailedMessageStream doesn't support successful streams");
        return null;
    }

    @Override
    MessageStream.Single<Message<Void>> completedSingleStreamTestSubject(Message<Void> message) {
        Assumptions.abort("FailedMessageStream doesn't support successful streams");
        return null;
    }

    @Override
    MessageStream.Empty<Message<Void>> completedEmptyStreamTestSubject() {
        Assumptions.abort("FailedMessageStream doesn't support successful streams");
        return null;
    }

    @Override
    MessageStream<Message<Void>> failingTestSubject(List<Message<Void>> messages,
                                                    Exception failure) {
        Assumptions.assumeTrue(messages.isEmpty(), "FailedMessageStream doesn't support content");
        return MessageStream.failed(failure);
    }

    @Override
    Message<Void> createRandomMessage() {
        Assumptions.abort("FailedMessageStream doesn't support content");
        return null;
    }
}