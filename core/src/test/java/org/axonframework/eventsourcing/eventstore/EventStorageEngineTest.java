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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.*;
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
        assertEquals(4, testSubject.readEvents(AGGREGATE).asStream().count());

        testSubject.appendEvents(createEvent("otherAggregate", 0));
        assertEquals(4, testSubject.readEvents(AGGREGATE).asStream().count());
        assertEquals(1, testSubject.readEvents("otherAggregate").asStream().count());
    }

    @Test
    public void testStoreAndLoadEventsArray() {
        testSubject.appendEvents(createEvent(0), createEvent(1));
        assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());
    }

    @Test
    public void testStoreAndLoadApplicationEvent() {
        testSubject.appendEvents(new GenericEventMessage<>("application event", MetaData.with("key", "value")));
        assertEquals(1, testSubject.readEvents(null, false).count());
        EventMessage<?> message = testSubject.readEvents(null, false).findFirst().get();
        assertEquals("application event", message.getPayload());
        assertEquals(MetaData.with("key", "value"), message.getMetaData());
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
        assertEquals(0L, testSubject.readEvents(randomUUID().toString()).asStream().count());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testReadPartialStream() {
        testSubject.appendEvents(createEvents(5));
        assertEquals(2L, testSubject.readEvents(AGGREGATE, 2).asStream().findFirst().get().getSequenceNumber());
        assertEquals(4L, testSubject.readEvents(AGGREGATE, 2).asStream().reduce((a, b) -> b).get().getSequenceNumber());
        assertEquals(3L, testSubject.readEvents(AGGREGATE, 2).asStream().count());
    }

    @Test
    public void testStoreAndLoadSnapshot() {
        testSubject.storeSnapshot(createEvent());
        assertTrue(testSubject.readSnapshot(AGGREGATE).isPresent());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testLoadTrackedEvents() throws InterruptedException {
        testSubject.appendEvents(createEvents(4));
        assertEquals(4, testSubject.readEvents(null, false).count());

        // give the clock some time to make sure the last message is really last
        Thread.sleep(10);

        DomainEventMessage<?> eventMessage = createEvent("otherAggregate", 0);
        testSubject.appendEvents(eventMessage);
        assertEquals(5, testSubject.readEvents(null, false).count());
        assertEquals(eventMessage.getIdentifier(),
                     testSubject.readEvents(null, false).reduce((a, b) -> b).get().getIdentifier());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testLoadPartialStreamOfTrackedEvents() {
        List<DomainEventMessage<?>> events = createEvents(4);
        testSubject.appendEvents(events);
        TrackingToken token = testSubject.readEvents(null, false).findFirst().get().trackingToken();
        assertEquals(3, testSubject.readEvents(token, false).count());
        assertEquals(events.subList(1, events.size()).stream().map(EventMessage::getIdentifier).collect(toList()),
                     testSubject.readEvents(token, false).map(EventMessage::getIdentifier).collect(toList()));
    }

    protected void setTestSubject(EventStorageEngine testSubject) {
        this.testSubject = testSubject;
    }

}
