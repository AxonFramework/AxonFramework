/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventstore.supporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventContainer;
import org.axonframework.domain.MetaData;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;

/**
 * @author Knut-Olav Hoven
 */
public class TimestampCutoffReadonlyEventStoreTest {

    @Test
    public void readEvents_givenNoEvents() {
        TimestampCutoffReadonlyEventStore es = givenNoEvents();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenEventsFromBackend() {
        TimestampCutoffReadonlyEventStore es = givenEventsFromBackend();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenVolatileEventsAndCutOffBackendEvents() {
        TimestampCutoffReadonlyEventStore es = givenCutOffBackendEvents();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void visitEvents_givenNoEvents() {
        TimestampCutoffReadonlyEventStore es = givenNoEvents();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).isEmpty();
    }

    @Test
    public void visitEvents_givenEventsFromBackend() {
        TimestampCutoffReadonlyEventStore es = givenEventsFromBackend();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(2);
        assertThat(visitor.visited().get(1).getSequenceNumber()).isEqualTo(1L);
    }

    @Test
    public void visitEvents_givenVolatileEventsAndCutOffBackendEvents() {
        TimestampCutoffReadonlyEventStore es = givenCutOffBackendEvents();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(0);
    }

    private TimestampCutoffReadonlyEventStore givenNoEvents() {
        VolatileEventStore backend = new VolatileEventStore();

        return new TimestampCutoffReadonlyEventStore(backend, backend, future());
    }

    private TimestampCutoffReadonlyEventStore givenEventsFromBackend() {
        VolatileEventStore backend = new VolatileEventStore();
        EventContainer ec = new EventContainer("My-1");
        ec.addEvent(MetaData.emptyInstance(), new MyEvent(1));
        ec.addEvent(MetaData.emptyInstance(), new MyEvent(2));
        backend.appendEvents("MyAggregate", ec.getEventStream());

        return new TimestampCutoffReadonlyEventStore(backend, backend, future());
    }

    private TimestampCutoffReadonlyEventStore givenCutOffBackendEvents() {
        VolatileEventStore backend = new VolatileEventStore();
        EventContainer ecBack = new EventContainer("My-1");
        ecBack.initializeSequenceNumber(0L);
        ecBack.addEvent(MetaData.emptyInstance(), new MyEvent("newer, cut off"));
        backend.appendEvents("MyAggregate", ecBack.getEventStream());

        return new TimestampCutoffReadonlyEventStore(backend, backend, past());
    }

    private static DateTime future() {
        return DateTime.now().plus(Duration.standardHours(1));
    }

    private static DateTime past() {
        return DateTime.now().minus(Duration.standardHours(1));
    }
}
