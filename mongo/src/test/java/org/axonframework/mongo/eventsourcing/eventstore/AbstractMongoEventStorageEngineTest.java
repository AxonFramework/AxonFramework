/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.mongo.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;

/**
 * Abstract test for {@link MongoEventStorageEngine} tests.
 *
 * @author Milan Savic
 */
public abstract class AbstractMongoEventStorageEngineTest extends BatchingEventStorageEngineTest {

    private MongoEventStorageEngine testSubject;

    @Override
    public void testCreateTailToken() {
        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:10.00Z"));
        testSubject.appendEvents(event1);

        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        testSubject.appendEvents(event2);

        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));
        testSubject.appendEvents(event3);

        TrackingToken headToken = testSubject.createTailToken();

        List<EventMessage<?>> readEvents = testSubject.readEvents(headToken, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event3, event2), readEvents);
    }

    @Override
    @Test
    public void testCreateTokenAt() {
        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:00.01Z"));
        testSubject.appendEvents(event1);

        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.00Z"));
        testSubject.appendEvents(event2);

        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));
        testSubject.appendEvents(event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event3, event2), readEvents);
    }

    @Override
    @Test
    public void testCreateTokenAtExactTime() {

        DomainEventMessage<String> event1 = createEvent(0, Instant.parse("2007-12-03T10:15:30.00Z"));
        DomainEventMessage<String> event2 = createEvent(1, Instant.parse("2007-12-03T10:15:40.01Z"));
        DomainEventMessage<String> event3 = createEvent(2, Instant.parse("2007-12-03T10:15:35.00Z"));

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event3, event2), readEvents);
    }

    @Override
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

        assertEventStreamsById(Arrays.asList(event4, event3), readEvents);
    }

    protected void setTestSubject(MongoEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
    }
}
