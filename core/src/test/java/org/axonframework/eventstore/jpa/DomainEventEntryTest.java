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

package org.axonframework.eventstore.jpa;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventstore.EventSerializer;
import org.joda.time.DateTime;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DomainEventEntryTest {

    private DomainEvent mockDomainEvent;
    private EventSerializer mockSerializer;
    private byte[] mockBytes = new byte[0];

    @Before
    public void setUp() {
        mockDomainEvent = mock(DomainEvent.class);
        mockSerializer = mock(EventSerializer.class);
        when(mockSerializer.serialize(mockDomainEvent)).thenReturn(mockBytes);
        when(mockSerializer.deserialize(mockBytes)).thenReturn(mockDomainEvent);
    }

    @Test
    public void testDomainEventEntry_WrapEventsCorrectly() {
        AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
        DateTime timestamp = new DateTime();
        UUID eventIdentifier = UUID.randomUUID();

        when(mockDomainEvent.getAggregateIdentifier()).thenReturn(aggregateIdentifier);
        when(mockDomainEvent.getSequenceNumber()).thenReturn(2L);
        when(mockDomainEvent.getTimestamp()).thenReturn(timestamp);
        when(mockDomainEvent.getEventIdentifier()).thenReturn(eventIdentifier);

        DomainEventEntry actualResult = new DomainEventEntry("test", mockDomainEvent,
                                                             mockSerializer.serialize(mockDomainEvent));

        assertEquals(aggregateIdentifier, actualResult.getAggregateIdentifier());
        assertEquals(2L, actualResult.getSequenceNumber());
        assertEquals(timestamp, actualResult.getTimestamp());
        assertEquals("test", actualResult.getType());
        assertEquals(mockDomainEvent, actualResult.getDomainEvent(mockSerializer));
        assertNull(actualResult.getId());
    }
}
