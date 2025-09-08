/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.LegacyResources;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SequentialPerAggregatePolicy}.
 *
 * @author Allard Buijze
 */
class SequentialPerAggregatePolicyTest {

    @Test
    void sequentialIdentifier() {
        // ok, pretty useless, but everything should be tested
        SequentialPerAggregatePolicy testSubject = new SequentialPerAggregatePolicy();
        String aggregateIdentifier = UUID.randomUUID().toString();
        EventWithProcessingContext case1 = eventWithProcessingContext(aggregateIdentifier);
        Object id1 = testSubject.getSequenceIdentifierFor(case1.event(), case1.processingContext()).orElse(null);
        Object id2 = testSubject.getSequenceIdentifierFor(case1.event(), case1.processingContext()).orElse(null);

        EventWithProcessingContext case2 = eventWithProcessingContext(UUID.randomUUID().toString());
        Object id3 = testSubject.getSequenceIdentifierFor(case2.event(), case2.processingContext()).orElse(null);
        StubProcessingContext processingContextWithoutAggregateIdentifierResource = new StubProcessingContext();
        Object id4 = testSubject.getSequenceIdentifierFor(
                new GenericEventMessage(new MessageType("event"), "bla"),
                processingContextWithoutAggregateIdentifierResource
        ).orElse(null);

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id2, id3);
        assertNull(id4);
    }

    @Nonnull
    private EventWithProcessingContext eventWithProcessingContext(String aggregateIdentifier) {
        EventMessage event = EventTestUtils.asEventMessage("payload");
        StubProcessingContext processingContext = new StubProcessingContext();
        processingContext.putResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, aggregateIdentifier);
        return new EventWithProcessingContext(event, processingContext);
    }

    private record EventWithProcessingContext(EventMessage event, StubProcessingContext processingContext) {

    }

}
