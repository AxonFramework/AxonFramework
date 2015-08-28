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

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Knut-Olav Hoven
 */
public class SequenceEventStoreTest {

    private static Instant future() {
        return Instant.now().plus(Duration.ofHours(1));
    }

    private static Instant past() {
        return Instant.now().minus(Duration.ofHours(1));
    }

    @Test
    public void readEvents_givenNoEvents() {
        SequenceEventStore es = givenNoEvents();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenFirstThrowingEventStreamNotFoundException() {
        SequenceEventStore es = givenFirstThrowingEventStreamNotFoundException();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenEventsFromBackend() {
        SequenceEventStore es = givenEventsFromBackend();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenVolatileEvents() {
        SequenceEventStore es = givenVolatileEvents();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenBackendEventsAndVolatileEvents() {
        SequenceEventStore es = givenBackendEventsAndVolatileEvents();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenVolatileEventsAndCutOffBackendEvents() {
        SequenceEventStore es = givenVolatileEventsAndCutOffBackendEvents();

        DomainEventStream readEvents = es.readEvents("My-1");

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
            public DomainEventStream readEvents(String identifier, long first, long last) {
                throw new EventStreamNotFoundException(identifier);
            }
        };
        return new SequenceEventStore(volatileEventStore, volatileEventStore, backend, backend);
    }

    private SequenceEventStore givenEventsFromBackend() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();

        VolatileEventStore backend = new VolatileEventStore();
        backend.appendEvents(Arrays.asList(createEventMessage(0, new MyEvent(1)),
                createEventMessage(1, new MyEvent(2))));
        TimestampCutoffReadonlyEventStore cutoffBackend = backend.cutoff(future());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, cutoffBackend, cutoffBackend);
    }

    private SequenceEventStore givenVolatileEvents() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();
        volatileEventStore.appendEvents(Arrays.asList(createEventMessage(0, new MyEvent(1)),
                createEventMessage(1, new MyEvent(2))));

        TimestampCutoffReadonlyEventStore backend = new VolatileEventStore().cutoff(future());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, backend, backend);
    }

    private SequenceEventStore givenBackendEventsAndVolatileEvents() {
        VolatileEventStore backend = new VolatileEventStore();
        backend.appendEvents(Collections.singletonList(createEventMessage(0, new MyEvent(1))));

        VolatileEventStore volatileEventStore = new VolatileEventStore();
        volatileEventStore.appendEvents(Collections.singletonList(createEventMessage(1, new MyEvent(2))));

        TimestampCutoffReadonlyEventStore cutoffBackend = backend.cutoff(future());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, cutoffBackend, cutoffBackend);
    }

    private SequenceEventStore givenVolatileEventsAndCutOffBackendEvents() {
        VolatileEventStore volatileEventStore = new VolatileEventStore();
        volatileEventStore.appendEvents(Collections.singletonList(createEventMessage(0, new MyEvent(1))));

        VolatileEventStore backend = new VolatileEventStore();
        backend.appendEvents(Collections.singletonList(createEventMessage(1, new MyEvent("newer, cut off"))));

        TimestampCutoffReadonlyEventStore cutoffBackend = backend.cutoff(past());

        return new SequenceEventStore(volatileEventStore, volatileEventStore, cutoffBackend, cutoffBackend);
    }

    private DomainEventMessage<?> createEventMessage(long sequenceNumber, Object event) {
        return new GenericDomainEventMessage<>("My-1", sequenceNumber, event);
    }
}
