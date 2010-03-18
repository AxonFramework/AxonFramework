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

package org.axonframework.core.repository.eventsourcing;

import org.axonframework.core.DomainEvent;
import org.axonframework.core.repository.eventsourcing.DomainEventEntry;
import org.axonframework.core.repository.eventsourcing.EventSerializer;
import org.joda.time.LocalDateTime;
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
        UUID aggregateIdentifier = UUID.randomUUID();
        LocalDateTime timestamp = new LocalDateTime();
        UUID eventIdentifier = UUID.randomUUID();

        when(mockDomainEvent.getAggregateIdentifier()).thenReturn(aggregateIdentifier);
        when(mockDomainEvent.getSequenceNumber()).thenReturn(2L);
        when(mockDomainEvent.getTimestamp()).thenReturn(timestamp);
        when(mockDomainEvent.getEventIdentifier()).thenReturn(eventIdentifier);

        DomainEventEntry actualResult = new DomainEventEntry("test", mockDomainEvent, mockSerializer);

        assertEquals(aggregateIdentifier, actualResult.getAggregateIdentifier());
        assertEquals(2L, actualResult.getSequenceIdentifier());
        assertEquals(timestamp, actualResult.getTimeStamp());
        assertEquals("test", actualResult.getType());
        assertEquals(mockDomainEvent, actualResult.getDomainEvent(mockSerializer));
        assertNull(actualResult.getId());
    }

}
