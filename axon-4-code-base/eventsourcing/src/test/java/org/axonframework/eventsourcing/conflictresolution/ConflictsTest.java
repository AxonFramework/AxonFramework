/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.eventhandling.DomainEventMessage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConflictsTest {

    private final List<DomainEventMessage<?>> events = IntStream.range(0, 10)
                                                                .mapToObj(sequenceNumber -> createEvent(AGGREGATE, sequenceNumber, PAYLOAD + sequenceNumber))
                                                                .collect(toList());

    @Test
    void payload() {
        assertTrue(Conflicts.payloadMatching(payload -> Objects.equals(payload, PAYLOAD + 2)).test(events));
        assertTrue(Conflicts.payloadMatching(String.class, payload -> Objects.equals(payload, PAYLOAD + 5)).test(events));
        assertFalse(Conflicts.payloadMatching(payload -> Objects.equals(payload, PAYLOAD + 11)).test(events));
    }

    @Test
    void payloadType() {
        assertTrue(Conflicts.payloadTypeOf(String.class).test(events));
        assertTrue(Conflicts.payloadTypeOf(Object.class).test(events));
        assertFalse(Conflicts.payloadTypeOf(Long.class).test(events));
    }

}
