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
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;

/**
 * @author Knut-Olav Hoven
 */
public class SequenceEventStoreTest {

    @Test
    public void readEvents_givenNoEvents() {
        SequenceEventStore es = givenNoEvents();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenFirstThrowingEventStreamNotFoundException() {
        SequenceEventStore es = givenFirstThrowingEventStreamNotFoundException();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenEventsFromBackend() {
        SequenceEventStore es = givenEventsFromBackend();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenVolatileEvents() {
        SequenceEventStore es = givenVolatileEvents();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenBackendEventsAndVolatileEvents() {
        SequenceEventStore es = givenBackendEventsAndVolatileEvents();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenVolatileEventsAndCutOffBackendEvents() {
        SequenceEventStore es = givenVolatileEventsAndCutOffBackendEvents();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void visitEvents_givenNoEvents() {
        SequenceEventStore es = givenNoEvents();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).isEmpty();
    }

    @Test
    public void visitEvents_givenEventsFromBackend() {
        SequenceEventStore es = givenEventsFromBackend();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(2);
        assertThat(visitor.visited().get(1).getSequenceNumber()).isEqualTo(1L);
    }

    @Test
    public void visitEvents_givenVolatileEvents() {
        SequenceEventStore es = givenVolatileEvents();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(2);
        assertThat(visitor.visited().get(1).getSequenceNumber()).isEqualTo(1L);
    }

    @Test
    public void visitEvents_givenBackendEventsAndVolatileEvents() {
        SequenceEventStore es = givenBackendEventsAndVolatileEvents();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(2);
        assertThat(visitor.visited().get(1).getSequenceNumber()).isEqualTo(1L);
    }

    @Test
    public void visitEvents_givenVolatileEventsAndCutOffBackendEvents() {
        SequenceEventStore es = givenVolatileEventsAndCutOffBackendEvents();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(1);
        assertThat(visitor.visited().get(0).getSequenceNumber()).isEqualTo(0L);
    }

    private SequenceEventStore givenNoEvents() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();
        TimestampCutoffReadonlyEventStore backend = new VolatileEventStore().cutoff(future());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, backend, backend);
    }

    private SequenceEventStore givenFirstThrowingEventStreamNotFoundException() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();
        VolatileEventStore backend = new VolatileEventStore() {
            @Override
            public DomainEventStream readEvents(String type, Object identifier) {
                throw new EventStreamNotFoundException(type, identifier);
            }
        };
        return new SequenceEventStore(volatileEventStore, volatileEventStore, backend, backend);
    }

    private SequenceEventStore givenEventsFromBackend() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();

        VolatileEventStore backend = new VolatileEventStore();
        EventContainer ec = new EventContainer("My-1");
        ec.addEvent(MetaData.emptyInstance(), new MyEvent(1));
        ec.addEvent(MetaData.emptyInstance(), new MyEvent(2));
        backend.appendEvents("MyAggregate", ec.getEventStream());
        TimestampCutoffReadonlyEventStore cutoffBackend = backend.cutoff(future());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, cutoffBackend, cutoffBackend);
    }

    private SequenceEventStore givenVolatileEvents() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();
        EventContainer ec = new EventContainer("My-1");
        ec.addEvent(MetaData.emptyInstance(), new MyEvent(1));
        ec.addEvent(MetaData.emptyInstance(), new MyEvent(2));
        volatileEventStore.appendEvents("MyAggregate", ec.getEventStream());

        TimestampCutoffReadonlyEventStore backend = new VolatileEventStore().cutoff(future());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, backend, backend);
    }

    private SequenceEventStore givenBackendEventsAndVolatileEvents() {
        VolatileEventStore backend = new VolatileEventStore();
        EventContainer ecBack = new EventContainer("My-1");
        ecBack.addEvent(MetaData.emptyInstance(), new MyEvent(1));
        backend.appendEvents("MyAggregate", ecBack.getEventStream());

        VolatileEventStore volatileEventStore = new VolatileEventStore();
        EventContainer ecVolatile = new EventContainer("My-1");
        ecVolatile.initializeSequenceNumber(0L);
        ecVolatile.addEvent(MetaData.emptyInstance(), new MyEvent(2));
        volatileEventStore.appendEvents("MyAggregate", ecVolatile.getEventStream());

        TimestampCutoffReadonlyEventStore cutoffBackend = backend.cutoff(future());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, cutoffBackend, cutoffBackend);
    }

    private SequenceEventStore givenVolatileEventsAndCutOffBackendEvents() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();
        EventContainer ecVolatile = new EventContainer("My-1");
        ecVolatile.addEvent(MetaData.emptyInstance(), new MyEvent(1));
        volatileEventStore.appendEvents("MyAggregate", ecVolatile.getEventStream());

        VolatileEventStore backend = new VolatileEventStore();
        EventContainer ecBack = new EventContainer("My-1");
        ecBack.initializeSequenceNumber(0L);
        ecBack.addEvent(MetaData.emptyInstance(), new MyEvent("newer, cut off"));
        backend.appendEvents("MyAggregate", ecBack.getEventStream());

        TimestampCutoffReadonlyEventStore cutoffBackend = backend.cutoff(past());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, cutoffBackend, cutoffBackend);
    }

    private static DateTime future() {
        return DateTime.now().plus(Duration.standardHours(1));
    }

    private static DateTime past() {
        return DateTime.now().minus(Duration.standardHours(1));
    }
}
