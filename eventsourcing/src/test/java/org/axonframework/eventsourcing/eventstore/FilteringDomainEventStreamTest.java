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

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.QualifiedNameUtils;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;

class FilteringDomainEventStreamTest {

    private DomainEventMessage<String> event1;
    private DomainEventMessage<String> event2;
    private DomainEventMessage<String> event3;

    @BeforeEach
    void setUp() throws Exception {
        event1 = new GenericDomainEventMessage<>("type", "1", 0L,
                                                 QualifiedNameUtils.fromDottedName("test.event"), "Create type 1");
        event2 = new GenericDomainEventMessage<>("type2", "1", 0L,
                                                 QualifiedNameUtils.fromDottedName("test.event"), "Create type 2");
        event3 = new GenericDomainEventMessage<>("type2", "1", 1L,
                                                 QualifiedNameUtils.fromDottedName("test.event"), "Change type 2");
    }

    @Test
    void forEachRemainingType1() {
        List<DomainEventMessage<String>> expectedMessages = Collections.singletonList(event1);

        DomainEventStream concat = new FilteringDomainEventStream(
                DomainEventStream.of(event1, event2, event3), // Initial stream - add all elements
                e -> e.getType().equals("type")
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertEquals(expectedMessages, actualMessages);
    }

    @Test
    void forEachRemainingType2() {
        List<DomainEventMessage<String>> expectedMessages = Arrays.asList(event2, event3);

        DomainEventStream concat = new FilteringDomainEventStream(
                DomainEventStream.of(event1, event2, event3), // Initial stream - add all elements
                e -> e.getType().equals("type2")
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertEquals(expectedMessages, actualMessages);
    }
}
