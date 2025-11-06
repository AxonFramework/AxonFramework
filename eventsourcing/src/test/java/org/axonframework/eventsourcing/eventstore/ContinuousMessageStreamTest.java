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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageStream.Empty;
import org.axonframework.messaging.core.MessageStream.Single;
import org.axonframework.messaging.core.MessageStreamTest;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.SimpleEntry;
import org.junit.jupiter.api.Assumptions;

import java.util.List;
import java.util.UUID;

public class ContinuousMessageStreamTest extends MessageStreamTest<EventMessage> {

    @Override
    protected MessageStream<EventMessage> completedTestSubject(List<EventMessage> messages) {
        return new ContinuousMessageStream<EventMessage>(
            last -> last == null ? messages : List.of(),
            m -> new SimpleEntry<>(m),
            (ms, r) -> () -> true
        );
    }

    @Override
    protected Single<EventMessage> completedSingleStreamTestSubject(EventMessage message) {
        Assumptions.abort("doesn't support explicit single-value streams");
        return null;
    }

    @Override
    protected Empty<EventMessage> completedEmptyStreamTestSubject() {
        Assumptions.abort("doesn't support explicitly empty streams");
        return null;
    }

    @Override
    protected MessageStream<EventMessage> failingTestSubject(List<EventMessage> messages, RuntimeException failure) {
        return new ContinuousMessageStream<EventMessage>(
            last -> {
                if (last == null && !messages.isEmpty()) {
                    return messages;
                }

                throw failure;
            },
            m -> new SimpleEntry<>(m),
            (ms, r) -> () -> true
        );
    }

    @Override
    protected EventMessage createRandomMessage() {
        return new GenericEventMessage(new MessageType("message"), UUID.randomUUID().toString());
    }

    @Override
    protected boolean isBoundedStream() {
        return false;
    }
}
