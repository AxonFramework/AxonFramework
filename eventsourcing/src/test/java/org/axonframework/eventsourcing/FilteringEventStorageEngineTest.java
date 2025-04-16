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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

class FilteringEventStorageEngineTest {

    private LegacyEventStorageEngine mockStorage;
    private FilteringEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        Predicate<EventMessage<?>> filter = m -> m.getPayload().toString().contains("accept");
        mockStorage = mock(LegacyEventStorageEngine.class);
        testSubject = new FilteringEventStorageEngine(mockStorage, filter);
    }

    @Test
    void eventsFromArrayMatchingAreForwarded() {
        EventMessage<String> event1 = EventTestUtils.asEventMessage("accept");
        EventMessage<String> event2 = EventTestUtils.asEventMessage("fail");
        EventMessage<String> event3 = EventTestUtils.asEventMessage("accept");

        testSubject.appendEvents(event1, event2, event3);

        verify(mockStorage).appendEvents(asList(event1, event3));
    }

    @Test
    void eventsFromListMatchingAreForwarded() {
        EventMessage<String> event1 = EventTestUtils.asEventMessage("accept");
        EventMessage<String> event2 = EventTestUtils.asEventMessage("fail");
        EventMessage<String> event3 = EventTestUtils.asEventMessage("accept");

        testSubject.appendEvents(asList(event1, event2, event3));

        verify(mockStorage).appendEvents(asList(event1, event3));
    }

    @Test
    void storeSnapshotDelegated() {
        DomainEventMessage<Object> snapshot = new GenericDomainEventMessage<>(
                "type", "id", 0, new MessageType("snapshot"), "fail"
        );
        testSubject.storeSnapshot(snapshot);

        verify(mockStorage).storeSnapshot(snapshot);
    }

    @Test
    void createTailTokenDelegated() {
        testSubject.createTailToken();

        verify(mockStorage).createTailToken();
    }

    @Test
    void createHeadTokenDelegated() {
        testSubject.createHeadToken();

        verify(mockStorage).createHeadToken();
    }

    @Test
    void createTokenAtDelegated() {
        Instant now = Instant.now();
        testSubject.createTokenAt(now);

        verify(mockStorage).createTokenAt(now);
    }
}
