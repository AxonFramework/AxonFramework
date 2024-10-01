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

/**
 * Test class validating the {@link SingleValueMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class SingleValueMessageStreamTest extends MessageStreamTest<String> {

    @Override
    MessageStream<String> testSubject(List<String> entries) {
        Assumptions.assumeTrue(entries.size() == 1, "SingleValueMessageStream only supports a single value");
        return MessageStream.just(entries.getFirst());
    }

    @Override
    MessageStream<String> failingTestSubject(List<String> entries, Exception failure) {
        Assumptions.assumeTrue(entries.isEmpty(),
                               "SingleValueMessageStream only supports failures without regular values");
        return MessageStream.fromFuture(CompletableFuture.failedFuture(failure));
    }

    @Override
    String createRandomEntry() {
        return "test-" + ThreadLocalRandom.current().nextInt(10000);
    }
}