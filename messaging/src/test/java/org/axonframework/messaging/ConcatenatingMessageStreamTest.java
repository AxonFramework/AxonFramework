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

class ConcatenatingMessageStreamTest extends MessageStreamTest<String> {

    @Override
    ConcatenatingMessageStream<Message<String>> createTestSubject(List<Message<String>> values) {
        if (values.isEmpty()) {
            return new ConcatenatingMessageStream<>(MessageStream.empty(), MessageStream.empty());
        } else if (values.size() == 1) {
            return new ConcatenatingMessageStream<>(MessageStream.just(values.getFirst()), MessageStream.empty());
        }
        return new ConcatenatingMessageStream<>(MessageStream.just(values.getFirst()),
                                                MessageStream.fromIterable(values.subList(1, values.size())));
    }

    @Override
    MessageStream<Message<String>> createTestSubject(List<Message<String>> values, Exception failure) {
        return createTestSubject(values).concatWith(MessageStream.failed(failure));
    }

    @Override
    String createRandomValidStreamEntry() {
        return null;
    }
}