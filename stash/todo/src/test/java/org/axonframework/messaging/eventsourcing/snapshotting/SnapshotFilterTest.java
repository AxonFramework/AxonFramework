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

package org.axonframework.messaging.eventsourcing.snapshotting;

import org.axonframework.messaging.eventhandling.DomainEventData;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.conversion.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SnapshotFilter}.
 *
 * @author Steven van Beelen
 */
class SnapshotFilterTest {

    private static final DomainEventData<?> NO_SNAPSHOT_DATA = null;
    private static final DomainEventData<?> MOCK_SNAPSHOT_DATA = mock(DomainEventData.class);
    private static final SnapshotEventEntry TEST_SNAPSHOT_DATA = new SnapshotEventEntry(new GenericDomainEventMessage(
            "some-type", "some-aggregate-id", 0, new MessageType("snapshot"), "some-payload"
    ), JacksonSerializer.defaultSerializer());

    @Test
    void allowAllReturnsTrue() {
        SnapshotFilter testSubject = SnapshotFilter.allowAll();

        assertTrue(testSubject.allow(NO_SNAPSHOT_DATA));
        assertTrue(testSubject.allow(MOCK_SNAPSHOT_DATA));
        assertTrue(testSubject.allow(TEST_SNAPSHOT_DATA));
    }

    @Test
    void rejectAllReturnsFalseOnAnyInput() {
        SnapshotFilter testSubject = SnapshotFilter.rejectAll();

        assertFalse(testSubject.allow(NO_SNAPSHOT_DATA));
        assertFalse(testSubject.allow(MOCK_SNAPSHOT_DATA));
        assertFalse(testSubject.allow(TEST_SNAPSHOT_DATA));
    }

    @Test
    void filterInvokesBothFiltersOnTrueForFirstFilter() {
        AtomicBoolean invokedFirst = new AtomicBoolean(false);
        SnapshotFilter first = snapshotData -> {
            invokedFirst.set(true);
            return true;
        };
        AtomicBoolean invokedSecond = new AtomicBoolean(false);
        SnapshotFilter second = snapshotData -> {
            invokedSecond.set(true);
            return false;
        };

        SnapshotFilter testSubject = first.combine(second);

        boolean result = testSubject.allow(mock(DomainEventData.class));
        assertFalse(result);
        assertTrue(invokedFirst.get());
        assertTrue(invokedSecond.get());
    }

    @Test
    void filterInvokesFirstFilterOnlyOnFalseForFirstFilter() {
        AtomicBoolean invokedFirst = new AtomicBoolean(false);
        SnapshotFilter first = snapshotData -> {
            invokedFirst.set(true);
            return false;
        };
        AtomicBoolean invokedSecond = new AtomicBoolean(false);
        SnapshotFilter second = snapshotData -> {
            invokedSecond.set(true);
            return true;
        };

        SnapshotFilter testSubject = first.combine(second);

        boolean result = testSubject.allow(mock(DomainEventData.class));
        assertFalse(result);
        assertTrue(invokedFirst.get());
        assertFalse(invokedSecond.get());
    }
}
