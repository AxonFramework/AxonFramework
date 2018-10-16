/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MetaData;
import org.junit.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.junit.Assert.*;

/**
 * @author Rene de Waele
 */
@Transactional
public abstract class EventStorageEngineTest {

    private EventStorageEngine testSubject;

    @After
    public void tearDown() {
        GenericEventMessage.clock = Clock.systemUTC();
    }

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
        testSubject.storeSnapshot(createEvent(0));
        testSubject.storeSnapshot(createEvent(1));
        testSubject.storeSnapshot(createEvent(3));
        testSubject.storeSnapshot(createEvent(2));
        assertTrue(testSubject.readSnapshot(AGGREGATE).isPresent());
        assertEquals(3, testSubject.readSnapshot(AGGREGATE).get().getSequenceNumber());
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

    @Test
    public void testCreateTailToken() {

        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:00.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken headToken = testSubject.createTailToken();

        List<EventMessage<?>> readEvents = testSubject.readEvents(headToken, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event2, event3), readEvents);
    }

    @Test
    public void testCreateHeadToken() {

        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:00.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken headToken = testSubject.createHeadToken();

        List<EventMessage<?>> readEvents = testSubject.readEvents(headToken, false)
                                                      .collect(toList());

        assertTrue(readEvents.isEmpty());
    }

    @Test
    public void testCreateTokenAt() {

        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:00.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event2, event3), readEvents);
    }

    @Test
    public void testCreateTokenAtExactTime() {

        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event2, event3), readEvents);
    }

    @Test
    public void testCreateTokenWithUnorderedEvents() {
        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:50.00Z"));
        DomainEventMessage<String> event4 = createEvent(3, Instant.parse("2007-12-03T10:15:45.00Z"));
        DomainEventMessage<String> event5 = createEvent(4, Instant.parse("2007-12-03T10:15:42.00Z"));

        testSubject.appendEvents(event1, event2, event3, event4, event5);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:45.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event3, event4, event5), readEvents);
    }

    protected void setTestSubject(EventStorageEngine testSubject) {
        this.testSubject = testSubject;
    }

    protected void assertEventStreamsById(List<EventMessage<?>> s1, List<EventMessage<?>> s2) {
        assertEquals(s1.stream().map(EventMessage::getIdentifier).collect(toList()),
                     s2.stream().map(EventMessage::getIdentifier).collect(toList()));
    }
}
