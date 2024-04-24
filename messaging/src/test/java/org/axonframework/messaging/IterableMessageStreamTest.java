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
import java.util.concurrent.ThreadLocalRandom;

class IterableMessageStreamTest extends MessageStreamTest<String> {

    @Override
    IterableMessageStream<Message<String>> createTestSubject(List<Message<String>> values) {
        return new IterableMessageStream<>(values);
    }

    @Override
    IterableMessageStream<Message<String>> createTestSubject(List<Message<String>> values, Exception failure) {
        Assumptions.abort("IterableMessageStream doesn't support failures");
        return null;
    }

    @Override
    String createRandomValidStreamEntry() {
        return "entry" + ThreadLocalRandom.current().nextInt(10000);
    }
}