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

package org.axonframework.eventsourcing.eventstore.inmemory;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStorageEngineTest;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link LegacyInMemoryEventStorageEngine}.
 *
 * @author Rene de Waele
 */
class LegacyInMemoryEventStorageEngineTest extends EventStorageEngineTest {

    private static final EventMessage<Object> TEST_EVENT = EventTestUtils.asEventMessage("test");

    private LegacyInMemoryEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new LegacyInMemoryEventStorageEngine();
        setTestSubject(testSubject);
    }

    @Test
    void publishedEventsEmittedToExistingStreams() {
        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        testSubject.appendEvents(TEST_EVENT);

        assertTrue(stream.findFirst().isPresent());
    }

    @Test
    void publishedEventsEmittedToExistingStreams_WithOffset() {
        testSubject = new LegacyInMemoryEventStorageEngine(1);
        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        testSubject.appendEvents(TEST_EVENT);

        Optional<? extends TrackedEventMessage<?>> optionalResult = stream.findFirst();
        assertTrue(optionalResult.isPresent());
        OptionalLong optionalResultPosition = optionalResult.get().trackingToken().position();
        assertTrue(optionalResultPosition.isPresent());
        assertEquals(1, optionalResultPosition.getAsLong());
    }

    @Test
    void eventsAreStoredOnCommitIfCurrentUnitOfWorkIsActive() {
        LegacyUnitOfWork<EventMessage<Object>> unitOfWork = LegacyDefaultUnitOfWork.startAndGet(TEST_EVENT);

        // when _only_ publishing...
        testSubject.appendEvents(TEST_EVENT);

        // then there are no events in the storage engine, since the UnitOfWork is not committed yet.
        Stream<? extends TrackedEventMessage<?>> eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());

        // When rolling back the UnitOfWork...
        unitOfWork.commit();

        // then there are *still* no events in the storage engine.
        eventStream = testSubject.readEvents(null, true);
        assertEquals(1L, eventStream.count());
    }

    @Test
    void eventsAreNotStoredWhenTheUnitOfWorkIsRolledBackIfCurrentUnitOfWorkIsActive() {
        LegacyUnitOfWork<EventMessage<Object>> unitOfWork = LegacyDefaultUnitOfWork.startAndGet(TEST_EVENT);

        // when _only_ publishing...
        testSubject.appendEvents(TEST_EVENT);

        // then there are no events in the storage engine, since the UnitOfWork is not committed yet.
        Stream<? extends TrackedEventMessage<?>> eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());

        // When rolling back the UnitOfWork...
        unitOfWork.rollback();

        // then there are *still* no events in the storage engine.
        eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());
    }
}
