/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.*;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asStream;
import static org.junit.Assert.*;

/**
 * @author Rene de Waele
 */
@Transactional
public abstract class EventStorageEngineTest {

    private EventStorageEngine testSubject;

    @Test
    public void testStoreAndLoadEvents() {
        testSubject.appendEvents(createEvents(4));
        assertEquals(4, asStream(testSubject.readEvents(AGGREGATE)).count());

        testSubject.appendEvents(createEvent("otherAggregate", 0));
        assertEquals(4, asStream(testSubject.readEvents(AGGREGATE)).count());
        assertEquals(1, asStream(testSubject.readEvents("otherAggregate")).count());
    }

    @Test
    public void testStoreAndLoadEventsArray() {
        testSubject.appendEvents(createEvent(0), createEvent(1));
        assertEquals(2, asStream(testSubject.readEvents(AGGREGATE)).count());
    }

    @Test
    public void testReturnedEventMessageBehavior() {
        testSubject.appendEvents(createEvent().withMetaData(singletonMap("key", "value")));
        DomainEventMessage<?> messageWithMetaData = testSubject.readEvents(AGGREGATE).next();

        /// we make sure persisted events have the same MetaData alteration logic
        DomainEventMessage<?> altered = messageWithMetaData.withMetaData(singletonMap("key2", "value"));
        DomainEventMessage<?> combined = messageWithMetaData.andMetaData(singletonMap("key2", "value"));
        assertTrue(altered.getMetaData().containsKey("key2"));
        altered.getPayload();
        assertFalse(altered.getMetaData().containsKey("key"));
        assertTrue(altered.getMetaData().containsKey("key2"));
        assertTrue(combined.getMetaData().containsKey("key"));
        assertTrue(combined.getMetaData().containsKey("key2"));
        assertNotNull(messageWithMetaData.getPayload());
        assertNotNull(messageWithMetaData.getMetaData());
        assertFalse(messageWithMetaData.getMetaData().isEmpty());
    }

    @Test
    public void testLoadNonExistent() {
        assertEquals(0L, asStream(testSubject.readEvents(randomUUID().toString())).count());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testReadPartialStream() {
        testSubject.appendEvents(createEvents(5));
        assertEquals(2L, asStream(testSubject.readEvents(AGGREGATE, 2)).findFirst().get().getSequenceNumber());
        assertEquals(4L, asStream(testSubject.readEvents(AGGREGATE, 2)).reduce((a, b) -> b).get().getSequenceNumber());
        assertEquals(3L, asStream(testSubject.readEvents(AGGREGATE, 2)).count());
    }

    @Test
    public void testStoreAndLoadSnapshot() {
        testSubject.storeSnapshot(createEvent());
        assertTrue(testSubject.readSnapshot(AGGREGATE).isPresent());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testLoadTrackedEvents() {
        testSubject.appendEvents(createEvents(4));
        assertEquals(4, testSubject.readEvents(null, false).count());

        DomainEventMessage<?> eventMessage = createEvent("otherAggregate", 0);
        testSubject.appendEvents(eventMessage);
        assertEquals(5, testSubject.readEvents(null, false).count());
        assertEquals(eventMessage.getIdentifier(),
                     testSubject.readEvents(null, false).reduce((a, b) -> b).get().getIdentifier());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testLoadPartialStreamOfTrackedEvents() {
        testSubject.appendEvents(createEvents(4));
        TrackingToken token = testSubject.readEvents(null, false).findFirst().get().trackingToken();
        assertEquals(3, testSubject.readEvents(token, false).count());
        assertTrue(testSubject.readEvents(token, false).allMatch(event -> event.trackingToken().isAfter(token)));
    }

    protected void setTestSubject(EventStorageEngine testSubject) {
        this.testSubject = testSubject;
    }

}
