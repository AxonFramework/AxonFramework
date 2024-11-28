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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.junit.jupiter.api.*;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DomainEventStream}.
 *
 * @author Allard Buijze
 */
class DomainEventStreamTest {

    @Test
    void peek() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>(
                "type", UUID.randomUUID().toString(), 0L, dottedName("test.event"), "Mock contents"
        );
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>(
                "type", UUID.randomUUID().toString(), 0L, dottedName("test.event"), "Mock contents"
        );
        DomainEventStream testSubject = DomainEventStream.of(event1, event2);
        assertSame(event1, testSubject.peek());
        assertSame(event1, testSubject.peek());
    }

    @Test
    void peek_EmptyStream() {
        DomainEventStream testSubject = DomainEventStream.of();
        assertFalse(testSubject.hasNext());
        try {
            testSubject.peek();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // what we expect
        }
    }

    @Test
    void nextAndHasNext() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>(
                "type", UUID.randomUUID().toString(), 0L, dottedName("test.event"), "Mock contents"
        );
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>(
                "type", UUID.randomUUID().toString(), 0L, dottedName("test.event"), "Mock contents"
        );
        DomainEventStream testSubject = DomainEventStream.of(event1, event2);
        assertTrue(testSubject.hasNext());
        assertSame(event1, testSubject.next());
        assertTrue(testSubject.hasNext());
        assertSame(event2, testSubject.next());
        assertFalse(testSubject.hasNext());
    }

    @Test
    void next_ReadBeyondEnd() {
        DomainEventMessage<String> event = new GenericDomainEventMessage<>(
                "type", UUID.randomUUID().toString(), 0L, dottedName("test.event"), "Mock contents"
        );
        DomainEventStream testSubject = DomainEventStream.of(event);
        testSubject.next();

        assertThrows(NoSuchElementException.class, testSubject::next);
    }
}
