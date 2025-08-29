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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.axonframework.eventhandling.DomainEventTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for {@link LegacyEventStorageEngine} implementations.
 * <p>
 * Methods are public so they can be overridden by {@link LegacyEventStorageEngine} implementation test cases in different
 * repository, like the [Mongo Extension](https://github.com/AxonFramework/extension-mongo).
 *
 * @author Rene de Waele
 */
@Transactional
public abstract class EventStorageEngineTest {

    private LegacyEventStorageEngine testSubject;

    @AfterEach
    public void tearDown() {
        GenericEventMessage.clock = Clock.systemUTC();
    }

    @Test
    public void storeAndLoadEvents() {
        testSubject.appendEvents(createDomainEvents(4));
        assertEquals(4, testSubject.readEvents(AGGREGATE).asStream().count());

        testSubject.appendEvents(createDomainEvent("otherAggregate", 0));
        assertEquals(4, testSubject.readEvents(AGGREGATE).asStream().count());
        assertEquals(1, testSubject.readEvents("otherAggregate").asStream().count());
    }

    @Test
    public void appendAndReadNonDomainEvent() {
        testSubject.appendEvents(new GenericEventMessage(new MessageType("event"), "Hello world"));

        List<? extends TrackedEventMessage> actual = testSubject.readEvents(null, false)
                                                                   .toList();
        assertEquals(1, actual.size());
        assertFalse(actual.getFirst() instanceof DomainEventMessage);
    }

    @Test
    public void storeAndLoadEventsArray() {
        testSubject.appendEvents(createDomainEvent(0), createDomainEvent(1));
        assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());
    }

    @Test
    public void storeAndLoadApplicationEvent() {
        EventMessage testEvent = new GenericEventMessage(
                new MessageType("event"), "application event", MetaData.with("key", "value")
        );
        testSubject.appendEvents(testEvent);
        assertEquals(1, testSubject.readEvents(null, false).count());
        Optional<? extends TrackedEventMessage> optionalFirst = testSubject.readEvents(null, false).findFirst();
        assertTrue(optionalFirst.isPresent());
        EventMessage message = optionalFirst.get();
        assertEquals("application event", message.payload());
        assertEquals(MetaData.with("key", "value"), message.metaData());
    }

    @Test
    public void returnedEventMessageBehavior() {
        testSubject.appendEvents(createDomainEvent().withMetaData(singletonMap("key", "value")));
        DomainEventMessage messageWithMetaData = testSubject.readEvents(AGGREGATE).next();

        /// we make sure persisted events have the same MetaData alteration logic
        DomainEventMessage altered = messageWithMetaData.withMetaData(singletonMap("key2", "value"));
        DomainEventMessage combined = messageWithMetaData.andMetaData(singletonMap("key2", "value"));
        assertTrue(altered.metaData().containsKey("key2"));
        altered.payload();
        assertFalse(altered.metaData().containsKey("key"));
        assertTrue(altered.metaData().containsKey("key2"));
        assertTrue(combined.metaData().containsKey("key"));
        assertTrue(combined.metaData().containsKey("key2"));
        assertNotNull(messageWithMetaData.payload());
        assertNotNull(messageWithMetaData.metaData());
        assertFalse(messageWithMetaData.metaData().isEmpty());
    }

    @Test
    public void loadNonExistent() {
        assertEquals(0L, testSubject.readEvents(randomUUID().toString()).asStream().count());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void readPartialStream() {
        testSubject.appendEvents(createDomainEvents(5));
        assertEquals(2L, testSubject.readEvents(AGGREGATE, 2).asStream().findFirst().get().getSequenceNumber());
        assertEquals(4L, testSubject.readEvents(AGGREGATE, 2).asStream().reduce((a, b) -> b).get().getSequenceNumber());
        assertEquals(3L, testSubject.readEvents(AGGREGATE, 2).asStream().count());
    }

    @Test
    public void storeAndLoadSnapshot() {
        testSubject.storeSnapshot(createDomainEvent(0));
        testSubject.storeSnapshot(createDomainEvent(1));
        testSubject.storeSnapshot(createDomainEvent(3));
        testSubject.storeSnapshot(createDomainEvent(2));
        assertTrue(testSubject.readSnapshot(AGGREGATE).isPresent());
        assertEquals(3, testSubject.readSnapshot(AGGREGATE).get().getSequenceNumber());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void loadTrackedEvents() throws InterruptedException {
        testSubject.appendEvents(createDomainEvents(4));
        assertEquals(4, testSubject.readEvents(null, false).count());

        // give the clock some time to make sure the last message is really last
        Thread.sleep(10);

        DomainEventMessage eventMessage = createDomainEvent("otherAggregate", 0);
        testSubject.appendEvents(eventMessage);
        assertEquals(5, testSubject.readEvents(null, false).count());
        assertEquals(eventMessage.identifier(),
                     testSubject.readEvents(null, false).reduce((a, b) -> b).get().identifier());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void loadPartialStreamOfTrackedEvents() {
        List<DomainEventMessage> events = createDomainEvents(4);
        testSubject.appendEvents(events);
        TrackingToken token = testSubject.readEvents(null, false).findFirst().get().trackingToken();
        assertEquals(3, testSubject.readEvents(token, false).count());
        assertEquals(events.subList(1, events.size()).stream().map(EventMessage::identifier).collect(toList()),
                     testSubject.readEvents(token, false).map(EventMessage::identifier).collect(toList()));
    }

    @Test
    public void createTailToken() {
        DomainEventMessage event1 = createDomainEvent(0, Instant.parse("2007-12-03T10:15:00.00Z"));
        DomainEventMessage event2 = createDomainEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage event3 = createDomainEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken headToken = testSubject.createTailToken();

        List<EventMessage> readEvents = testSubject.readEvents(headToken, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event2, event3), readEvents);
    }

    @Test
    public void createHeadToken() {
        DomainEventMessage event1 = createDomainEvent(0, Instant.parse("2007-12-03T10:15:00.00Z"));
        DomainEventMessage event2 = createDomainEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage event3 = createDomainEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken headToken = testSubject.createHeadToken();

        List<EventMessage> readEvents = testSubject.readEvents(headToken, false)
                                                      .collect(toList());

        assertTrue(readEvents.isEmpty());
    }

    @Test
    public void createTokenAt() {
        DomainEventMessage event1 = createDomainEvent(0, Instant.parse("2007-12-03T10:15:00.00Z"));
        DomainEventMessage event2 = createDomainEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage event3 = createDomainEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event2, event3), readEvents);
    }

    @Test
    public void createTokenAtExactTime() {
        DomainEventMessage event1 = createDomainEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage event2 = createDomainEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage event3 = createDomainEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event2, event3), readEvents);
    }

    @Test
    public void createTokenWithUnorderedEvents() {
        DomainEventMessage event1 = createDomainEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage event2 = createDomainEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage event3 = createDomainEvent(2, Instant.parse("2007-12-03T10:15:50.00Z"));
        DomainEventMessage event4 = createDomainEvent(3, Instant.parse("2007-12-03T10:15:45.00Z"));
        DomainEventMessage event5 = createDomainEvent(4, Instant.parse("2007-12-03T10:15:42.00Z"));

        testSubject.appendEvents(event1, event2, event3, event4, event5);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:45.00Z"));

        List<EventMessage> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event3, event4, event5), readEvents);
    }

    /**
     * If the dateTime is after the last event in the store, the token should default to the position of the last
     * event.
     */
    @Test
    public void createTokenAtTimeAfterLastEvent() {
        Instant dateTimeAfterLastEvent = Instant.parse("2008-12-03T10:15:30.00Z");

        DomainEventMessage event1 = createDomainEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage event2 = createDomainEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage event3 = createDomainEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));
        testSubject.appendEvents(event1, event2, event3);

        TrackingToken result = testSubject.createTokenAt(dateTimeAfterLastEvent);

        List<EventMessage> readEvents = testSubject.readEvents(result, false).collect(toList());

        assertTrue(readEvents.isEmpty());
    }

    /**
     * If the dateTime is before the first event in the store, the token should default to the position of the first
     * event.
     */
    @Test
    public void createTokenAtTimeBeforeFirstEvent() {
        Instant dateTimeBeforeFirstEvent = Instant.parse("2006-12-03T10:15:30.00Z");

        DomainEventMessage event1 = createDomainEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage event2 = createDomainEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        DomainEventMessage event3 = createDomainEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));
        testSubject.appendEvents(event1, event2, event3);

        TrackingToken result = testSubject.createTokenAt(dateTimeBeforeFirstEvent);

        List<EventMessage> readEvents = testSubject.readEvents(result, false).collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event2, event3), readEvents);
    }

    protected void setTestSubject(LegacyEventStorageEngine testSubject) {
        this.testSubject = testSubject;
    }

    protected void assertEventStreamsById(List<EventMessage> s1, List<EventMessage> s2) {
        assertEquals(s1.stream().map(EventMessage::identifier).collect(toList()),
                     s2.stream().map(EventMessage::identifier).collect(toList()));
    }
}
