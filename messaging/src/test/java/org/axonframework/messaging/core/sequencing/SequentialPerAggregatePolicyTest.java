/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SequentialPerAggregatePolicy}.
 *
 * @author Allard Buijze
 */
class SequentialPerAggregatePolicyTest {

    private final SequentialPerAggregatePolicy testSubject = SequentialPerAggregatePolicy.INSTANCE;

    @Test
    void sameAggregateIdentifierProducesSameSequentialIdentifier() {
        // given
        String aggregateIdentifier = UUID.randomUUID().toString();
        EventWithProcessingContext event = eventWithProcessingContext(aggregateIdentifier);

        // when
        Object id1 = testSubject.getSequenceIdentifierFor(event.event(), event.processingContext()).orElse(null);
        Object id2 = testSubject.getSequenceIdentifierFor(event.event(), event.processingContext()).orElse(null);

        // then
        assertEquals(id1, id2);
    }

    @Test
    void differentAggregateIdentifiersProduceDifferentSequentialIdentifiers() {
        // given
        String aggregateIdentifier1 = UUID.randomUUID().toString();
        String aggregateIdentifier2 = UUID.randomUUID().toString();
        EventWithProcessingContext event1 = eventWithProcessingContext(aggregateIdentifier1);
        EventWithProcessingContext event2 = eventWithProcessingContext(aggregateIdentifier2);

        // when
        Object id1 = testSubject.getSequenceIdentifierFor(event1.event(), event1.processingContext()).orElse(null);
        Object id2 = testSubject.getSequenceIdentifierFor(event2.event(), event2.processingContext()).orElse(null);

        // then
        assertNotEquals(id1, id2);
    }

    @Test
    void processingContextWithoutAggregateIdentifierReturnsEmpty() {
        // given
        StubProcessingContext processingContextWithoutAggregateIdentifierResource = new StubProcessingContext();
        EventMessage event = new GenericEventMessage(new MessageType("event"), "bla");

        // when
        Object sequenceIdentifier = testSubject.getSequenceIdentifierFor(
                event,
                processingContextWithoutAggregateIdentifierResource
        ).orElse(null);

        // then
        assertNull(sequenceIdentifier);
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
