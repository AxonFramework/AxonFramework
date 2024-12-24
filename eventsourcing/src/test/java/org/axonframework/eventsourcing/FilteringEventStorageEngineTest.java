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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

class FilteringEventStorageEngineTest {

    private EventStorageEngine mockStorage;
    private FilteringEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        Predicate<EventMessage<?>> filter = m -> m.getPayload().toString().contains("accept");
        mockStorage = mock(EventStorageEngine.class);
        testSubject = new FilteringEventStorageEngine(mockStorage, filter);
    }

    @Test
    void eventsFromArrayMatchingAreForwarded() {
        EventMessage<String> event1 = GenericEventMessage.asEventMessage("accept");
        EventMessage<String> event2 = GenericEventMessage.asEventMessage("fail");
        EventMessage<String> event3 = GenericEventMessage.asEventMessage("accept");

        testSubject.appendEvents(event1, event2, event3);

        verify(mockStorage).appendEvents(asList(event1, event3));
    }

    @Test
    void eventsFromListMatchingAreForwarded() {
        EventMessage<String> event1 = GenericEventMessage.asEventMessage("accept");
        EventMessage<String> event2 = GenericEventMessage.asEventMessage("fail");
        EventMessage<String> event3 = GenericEventMessage.asEventMessage("accept");

        testSubject.appendEvents(asList(event1, event2, event3));

        verify(mockStorage).appendEvents(asList(event1, event3));
    }

    @Test
    void storeSnapshotDelegated() {
        DomainEventMessage<Object> snapshot = new GenericDomainEventMessage<>(
                "type", "id", 0, new QualifiedName("test", "snapshot", "0.0.1"), "fail"
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
