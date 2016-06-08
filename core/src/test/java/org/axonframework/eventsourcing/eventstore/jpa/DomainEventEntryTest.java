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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class DomainEventEntryTest {

    @Test
    public void testDomainEventEntry_WrapEventsCorrectly() {
        Serializer serializer = new XStreamSerializer();

        String payload = "Payload";
        MetaData metaData = new MetaData(Collections.singletonMap("Key", "Value"));
        DomainEventMessage<String> event = new GenericDomainEventMessage<>("type", randomUUID().toString(), 2L, payload,
                                                                           metaData, randomUUID().toString(),
                                                                           Instant.now());

        DomainEventEntry eventEntry = new DomainEventEntry(event, serializer);

        assertEquals(event.getAggregateIdentifier(), eventEntry.getAggregateIdentifier());
        assertEquals(event.getSequenceNumber(), eventEntry.getSequenceNumber());
        assertEquals(event.getTimestamp(), eventEntry.getTimestamp());
        assertEquals(payload, serializer.deserialize(eventEntry.getPayload()));
        assertEquals(metaData, serializer.deserialize(eventEntry.getMetaData()));
        assertEquals(byte[].class, eventEntry.getPayload().getContentType());
    }
}
