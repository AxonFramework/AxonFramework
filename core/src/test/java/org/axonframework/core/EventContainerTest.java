/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.core;

import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class EventContainerTest {

    @Test
    public void testAddEvent_IdAndSequenceNumberInitialized() {
        UUID identifier = UUID.randomUUID();
        StubDomainEvent domainEvent = new StubDomainEvent();

        EventContainer eventContainer = new EventContainer(identifier);
        assertEquals(identifier, eventContainer.getAggregateIdentifier());
        eventContainer.setFirstSequenceNumber(12);

        assertEquals(0, eventContainer.size());
        assertFalse(eventContainer.getEventStream().hasNext());

        eventContainer.addEvent(domainEvent);

        assertEquals(1, eventContainer.size());
        assertEquals(new Long(12), domainEvent.getSequenceNumber());
        assertEquals(identifier, domainEvent.getAggregateIdentifier());
        assertTrue(eventContainer.getEventStream().hasNext());

        eventContainer.clear();

        assertEquals(0, eventContainer.size());
    }

    @Test
    public void testAddEventWithId_IdConflictsWithContainerId() {
        UUID identifier = UUID.randomUUID();
        StubDomainEvent domainEvent = new StubDomainEvent(identifier);

        EventContainer eventContainer = new EventContainer(UUID.randomUUID());
        eventContainer.setFirstSequenceNumber(12);

        try {
            eventContainer.addEvent(domainEvent);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("identifier"));
            assertTrue(e.getMessage().toLowerCase().contains("match"));
        }
    }

    @Test
    public void testAddEvent_SequenceNumberInitialized() {
        UUID identifier = UUID.randomUUID();
        StubDomainEvent domainEvent = new StubDomainEvent(identifier);
        StubDomainEvent domainEvent2 = new StubDomainEvent(identifier);
        domainEvent.setSequenceNumber(123);

        EventContainer eventContainer = new EventContainer(identifier);

        eventContainer.addEvent(domainEvent);
        eventContainer.addEvent(domainEvent2);

        assertEquals(new Long(124), domainEvent2.getSequenceNumber());
    }

}
