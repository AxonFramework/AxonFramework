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

class FailedMessageStreamTest extends MessageStreamTest<Void> {

    @Override
    FailedMessageStream<Message<Void>> createTestSubject(List<Message<Void>> values) {
        Assumptions.abort("FailedMessageStream doesn't support successful streams");
        return null;
    }

    @Override
    FailedMessageStream<Message<Void>> createTestSubject(List<Message<Void>> values, Exception failure) {
        Assumptions.assumeTrue(values.isEmpty(), "FailedMessageStream doesn't support content");
        return new FailedMessageStream<>(failure);
    }

    @Override
    Void createRandomValidStreamEntry() {
        Assumptions.abort("FailedMessageStream doesn't support content");
        return null;
    }
}