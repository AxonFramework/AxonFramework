/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.domain;

import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class EventContainerTest {

    private MetaData metaData = MetaData.emptyInstance();
    private String aggregateType = "type";

    @Test
    public void testAddEvent_IdAndSequenceNumberInitialized() {
        final String identifier = UUID.randomUUID().toString();

        EventContainer eventContainer = new EventContainer(identifier);
        assertEquals(identifier, eventContainer.getAggregateIdentifier());
        eventContainer.initializeSequenceNumber(11L);

        assertEquals(0, eventContainer.size());
        assertFalse(eventContainer.getEventStream().hasNext());

        eventContainer.addEvent(metaData, new Object());

        assertEquals(1, eventContainer.size());
        DomainEventMessage domainEvent = eventContainer.getEventList().get(0);
        assertEquals(12L, domainEvent.getSequenceNumber());
        assertEquals(identifier, domainEvent.getAggregateIdentifier());
        assertTrue(eventContainer.getEventStream().hasNext());

        eventContainer.commit();

        assertEquals(0, eventContainer.size());
    }
}
