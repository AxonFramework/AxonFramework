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
 * Test class validating the {@link CompletionCallbackMessageStream} through the {@link MessageStreamTest} suite.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class CompletionCallbackMessageStreamTest extends MessageStreamTest<String> {

    private static final Runnable NO_OP_COMPLETION_CALLBACK = () -> {
    };

    @Override
    MessageStream<String> testSubject(List<String> entries) {
        return new CompletionCallbackMessageStream<>(MessageStream.fromIterable(entries), NO_OP_COMPLETION_CALLBACK);
    }

    @Override
    MessageStream<String> failingTestSubject(List<String> entries, Exception failure) {
        return new CompletionCallbackMessageStream<>(MessageStream.fromIterable(entries)
                                                                  .concatWith(MessageStream.failed(failure)),
                                                     NO_OP_COMPLETION_CALLBACK);
    }

    @Override
    String createRandomEntry() {
        return "test-" + ThreadLocalRandom.current().nextInt(10000);
    }
}