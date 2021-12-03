/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link InMemoryDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
class InMemoryDeadLetterQueueTest extends DeadLetterQueueTest<EventMessage<?>> {

    @Override
    DeadLetterQueue<EventMessage<?>> buildTestSubject() {
        return InMemoryDeadLetterQueue.defaultQueue();
    }

    @Override
    EventMessage<?> generateMessage() {
        return GenericEventMessage.asEventMessage("Then this happened..." + UUID.randomUUID());
    }

    @Test
    void testMaxSize() {
        int expectedMaxEntries = 128;

        InMemoryDeadLetterQueue<Message<?>> testSubject = InMemoryDeadLetterQueue.builder()
                                                                                 .maxEntries(expectedMaxEntries)
                                                                                 .build();

        assertEquals(expectedMaxEntries, testSubject.maxSize());
    }
}