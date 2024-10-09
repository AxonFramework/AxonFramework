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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class validating the {@code static} methods of the {@link IndexedEventMessage}.
 *
 * @author Steven van Beelen
 */
class IndexedEventMessageTest {

    private static final EventMessage<String> TEST_EVENT = GenericEventMessage.asEventMessage("some-event");
    private static final Set<Index> TEST_INDICES = Set.of(new Index("key", "value"));

    @Test
    void wrapIntoIndexedEventMessage() {
        IndexedEventMessage<String> result = IndexedEventMessage.asIndexedEvent(TEST_EVENT, TEST_INDICES);

        assertEquals(TEST_EVENT.getIdentifier(), result.getIdentifier());
        assertEquals(TEST_EVENT.getPayload(), result.getPayload());
        assertEquals(TEST_EVENT.getMetaData(), result.getMetaData());
        assertEquals(TEST_EVENT.getTimestamp(), result.getTimestamp());
        assertEquals(TEST_INDICES, result.indices());
    }

    @Test
    void mergeIndicesOfGivenIndexedEventMessageAndGivenIndices() {
        IndexedEventMessage<String> testEvent = new GenericIndexedEventMessage<>(TEST_EVENT, TEST_INDICES);
        Set<Index> testIndices = Set.of(new Index("newKey", "newValue"));
        Set<Index> expectedIndices = new HashSet<>(TEST_INDICES);
        expectedIndices.addAll(testIndices);

        IndexedEventMessage<String> result = IndexedEventMessage.asIndexedEvent(testEvent, testIndices);

        assertEquals(testEvent.getIdentifier(), result.getIdentifier());
        assertEquals(testEvent.getPayload(), result.getPayload());
        assertEquals(testEvent.getMetaData(), result.getMetaData());
        assertEquals(testEvent.getTimestamp(), result.getTimestamp());
        assertEquals(expectedIndices, result.indices());
    }
}