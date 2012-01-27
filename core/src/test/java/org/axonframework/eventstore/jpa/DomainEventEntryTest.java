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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.eventstore.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;
import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DomainEventEntryTest {

    private DomainEventMessage mockDomainEvent;
    private SerializedObject mockPayload = new SimpleSerializedObject("PayloadBytes".getBytes(), "Mock", 0);
    private SerializedObject mockMetaData = new SerializedMetaData("MetaDataBytes".getBytes());
    private Serializer mockSerializer;
    private MetaData metaData;

    @Before
    public void setUp() {
        mockDomainEvent = mock(DomainEventMessage.class);
        mockSerializer = mock(Serializer.class);
        metaData = new MetaData(Collections.singletonMap("Key", "Value"));
        when(mockSerializer.deserialize(mockPayload)).thenReturn(Arrays.asList((Object)"Payload"));
        when(mockSerializer.deserialize(mockMetaData)).thenReturn(Arrays.asList(((Object)metaData)));
        List<Class> payloadType = new ArrayList<Class>();
        payloadType.add(String.class);
        when(mockSerializer.classForType(mockPayload.getType())).thenReturn(payloadType);
    }

    @Test
    public void testDomainEventEntry_WrapEventsCorrectly() {
        UUID aggregateIdentifier = UUID.randomUUID();
        DateTime timestamp = new DateTime();
        UUID eventIdentifier = UUID.randomUUID();

        when(mockDomainEvent.getAggregateIdentifier()).thenReturn(aggregateIdentifier);
        when(mockDomainEvent.getSequenceNumber()).thenReturn(2L);
        when(mockDomainEvent.getTimestamp()).thenReturn(timestamp);
        when(mockDomainEvent.getIdentifier()).thenReturn(eventIdentifier.toString());
        when(mockDomainEvent.getPayloadType()).thenReturn(String.class);

        DomainEventEntry actualResult = new DomainEventEntry("test", mockDomainEvent, mockPayload, mockMetaData);

        assertEquals(aggregateIdentifier.toString(), actualResult.getAggregateIdentifier());
        assertEquals(2L, actualResult.getSequenceNumber());
        assertEquals(timestamp, actualResult.getTimestamp());
        assertEquals("test", actualResult.getType());
        DomainEventMessage<?> domainEvent = actualResult.getDomainEvent(mockSerializer).get(0);
        assertTrue(domainEvent instanceof SerializedDomainEventMessage);
        verify(mockSerializer, never()).deserialize(mockPayload);
        verify(mockSerializer, never()).deserialize(mockMetaData);

        assertEquals("Payload", domainEvent.getPayload());
        verify(mockSerializer).deserialize(mockPayload);
        verify(mockSerializer, never()).deserialize(mockMetaData);

        assertEquals(metaData, domainEvent.getMetaData());
        verify(mockSerializer).deserialize(mockMetaData);
    }
}
