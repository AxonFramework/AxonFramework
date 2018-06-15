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
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.junit.*;

import java.time.Clock;
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
    @Test
    public void testCreateTokenAt() {
        GenericEventMessage.clock = Clock.fixed(Instant.parse("2007-12-03T10:15:00.01Z"), Clock.systemUTC().getZone());
        DomainEventMessage<String> event1 = createEvent(0);
        testSubject.appendEvents(event1);

        GenericEventMessage.clock = Clock.fixed(Instant.parse("2007-12-03T10:15:40.00Z"), Clock.systemUTC().getZone());
        DomainEventMessage<String> event2 = createEvent(1);
        testSubject.appendEvents(event2);

        GenericEventMessage.clock = Clock.fixed(Instant.parse("2007-12-03T10:15:35.00Z"), Clock.systemUTC().getZone());
        DomainEventMessage<String> event3 = createEvent(2);
        testSubject.appendEvents(event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event3, event2), readEvents);
    }

    @Override
    @Test
    public void testCreateTokenAtExactTime() {
        GenericEventMessage.clock = Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), Clock.systemUTC().getZone());
        DomainEventMessage<String> event1 = createEvent(0);

        GenericEventMessage.clock = Clock.fixed(Instant.parse("2007-12-03T10:15:40.01Z"), Clock.systemUTC().getZone());
        DomainEventMessage<String> event2 = createEvent(1);

        GenericEventMessage.clock = Clock.fixed(Instant.parse("2007-12-03T10:15:35.00Z"), Clock.systemUTC().getZone());
        DomainEventMessage<String> event3 = createEvent(2);

        testSubject.appendEvents(event1, event2, event3);

        TrackingToken tokenAt = testSubject.createTokenAt(Instant.parse("2007-12-03T10:15:30.00Z"));

        List<EventMessage<?>> readEvents = testSubject.readEvents(tokenAt, false)
                                                      .collect(toList());

        assertEventStreamsById(Arrays.asList(event1, event3, event2), readEvents);
    }

    protected void setTestSubject(MongoEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
    }
}
