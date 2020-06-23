/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventsourcing.snapshotting;

import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.serialization.xml.XStreamSerializer;
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
    private static final SnapshotEventEntry TEST_SNAPSHOT_DATA = new SnapshotEventEntry(
            new GenericDomainEventMessage<>("some-type", "some-aggregate-id", 0, "some-payload"),
            XStreamSerializer.defaultSerializer()
    );

    @Test
    void testAllowAllReturnsTrue() {
        SnapshotFilter testSubject = SnapshotFilter.allowAll();

        assertTrue(testSubject.allow(NO_SNAPSHOT_DATA));
        assertTrue(testSubject.allow(MOCK_SNAPSHOT_DATA));
        assertTrue(testSubject.allow(TEST_SNAPSHOT_DATA));
    }

    @Test
    void testRejectAllReturnsFalseOnAnyInput() {
        SnapshotFilter testSubject = SnapshotFilter.rejectAll();

        assertFalse(testSubject.allow(NO_SNAPSHOT_DATA));
        assertFalse(testSubject.allow(MOCK_SNAPSHOT_DATA));
        assertFalse(testSubject.allow(TEST_SNAPSHOT_DATA));
    }

    @Test
    void testFilterInvokesBothFiltersOnTrueForFirstFilter() {
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
    void testFilterInvokesFirstFilterOnlyOnFalseForFirstFilter() {
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
