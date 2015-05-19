/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.junit.*;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DomainEventEntryTest {

    private DomainEventMessage mockDomainEvent;
    private SerializedObject<byte[]> mockPayload = new SimpleSerializedObject<byte[]>("PayloadBytes".getBytes(),
                                                                                      byte[].class, "Mock", "0");
    private SerializedObject<byte[]> mockMetaData = new SerializedMetaData<byte[]>("MetaDataBytes".getBytes(),
                                                                                   byte[].class);

    @Before
    public void setUp() {
        mockDomainEvent = mock(DomainEventMessage.class);
        Serializer mockSerializer = mock(Serializer.class);
        MetaData metaData = new MetaData(Collections.singletonMap("Key", "Value"));
        when(mockSerializer.deserialize(mockPayload)).thenReturn("Payload");
        when(mockSerializer.deserialize(isA(SerializedMetaData.class))).thenReturn(metaData);
    }

    @Test
    public void testDomainEventEntry_WrapEventsCorrectly() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        ZonedDateTime timestamp = ZonedDateTime.now();
        UUID eventIdentifier = UUID.randomUUID();

        when(mockDomainEvent.getAggregateIdentifier()).thenReturn(aggregateIdentifier);
        when(mockDomainEvent.getSequenceNumber()).thenReturn(2L);
        when(mockDomainEvent.getTimestamp()).thenReturn(timestamp);
        when(mockDomainEvent.getIdentifier()).thenReturn(eventIdentifier.toString());
        when(mockDomainEvent.getPayloadType()).thenReturn(String.class);

        DomainEventEntry actualResult = new DomainEventEntry("test", mockDomainEvent, mockPayload, mockMetaData);

        assertEquals(aggregateIdentifier, actualResult.getAggregateIdentifier());
        assertEquals(2L, actualResult.getSequenceNumber());
        assertEquals(timestamp, actualResult.getTimestamp());
        assertEquals("test", actualResult.getType());
    }
}
