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
import java.util.function.Function;

/**
 * Test class validating the {@link MappedMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class MappedMessageStreamTest extends MessageStreamTest<String> {

    private static final Function<String, String> NO_OP_MAPPER = message -> message;

    @Override
    MessageStream<String> testSubject(List<String> entries) {
        return new MappedMessageStream<>(MessageStream.fromIterable(entries), NO_OP_MAPPER);
    }

    @Override
    MessageStream<String> failingTestSubject(List<String> entries, Exception failure) {
        return new MappedMessageStream<>(MessageStream.fromIterable(entries)
                                                      .concatWith(MessageStream.failed(failure)),
                                         NO_OP_MAPPER);
    }

    @Override
    String createRandomEntry() {
        return "test-" + ThreadLocalRandom.current().nextInt(10000);
    }
}