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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.common.DateTimeUtils;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.utils.TestSerializer;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Collections;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DomainEventEntry}.
 *
 * @author Allard Buijze
 */
class DomainEventEntryTest {

    private final Serializer serializer = TestSerializer.xStreamSerializer();

    @Test
    void domainEventEntryWrapEventsCorrectly() {
        Instant testTimestamp = Instant.now();

        String expectedAggregateType = "aggregateType";
        String expectedAggregateId = randomUUID().toString();
        long expectedSequenceNumber = 2L;
        QualifiedName expectedType = new QualifiedName("test", "event", "0.0.1");
        String expectedPayload = "Payload";
        MetaData expectedMetaData = new MetaData(Collections.singletonMap("Key", "Value"));
        Instant expectedTimestamp = DateTimeUtils.parseInstant(DateTimeUtils.formatInstant(testTimestamp));
        String expectedEventIdentifier = randomUUID().toString();

        DomainEventMessage<String> testEvent = new GenericDomainEventMessage<>(
                expectedAggregateType, expectedAggregateId, expectedSequenceNumber,
                expectedEventIdentifier, expectedType,
                expectedPayload, expectedMetaData, testTimestamp
        );

        DomainEventEntry result = new DomainEventEntry(testEvent, serializer);

        assertEquals(expectedAggregateType, result.getType());
        assertEquals(expectedAggregateId, result.getAggregateIdentifier());
        assertEquals(expectedSequenceNumber, result.getSequenceNumber());
        assertEquals(expectedEventIdentifier, result.getEventIdentifier());
        assertEquals(expectedTimestamp, result.getTimestamp());
        assertEquals(expectedPayload, serializer.deserialize(result.getPayload()));
        assertEquals(byte[].class, result.getPayload().getContentType());
        assertEquals(expectedMetaData, serializer.deserialize(result.getMetaData()));
    }
}
