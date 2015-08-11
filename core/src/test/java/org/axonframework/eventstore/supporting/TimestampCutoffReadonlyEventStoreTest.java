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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Knut-Olav Hoven
 */
public class TimestampCutoffReadonlyEventStoreTest {

    private static Instant future() {
        return Instant.now().plus(Duration.ofHours(1));
    }

    private static Instant past() {
        return Instant.now().minus(Duration.ofHours(1));
    }

    @Test
    public void readEvents_givenNoEvents() {
        TimestampCutoffReadonlyEventStore es = givenNoEvents();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenEventsFromBackend() {
        TimestampCutoffReadonlyEventStore es = givenEventsFromBackend();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenVolatileEventsAndCutOffBackendEvents() {
        TimestampCutoffReadonlyEventStore es = givenCutOffBackendEvents();

        DomainEventStream readEvents = es.readEvents("My-1");

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
        backend.appendEvents(createEventMessage(0, new MyEvent(1)), createEventMessage(1, new MyEvent(2)));
        return new TimestampCutoffReadonlyEventStore(backend, backend, future());
    }

    private TimestampCutoffReadonlyEventStore givenCutOffBackendEvents() {
        VolatileEventStore backend = new VolatileEventStore();
        backend.appendEvents(createEventMessage(1, new MyEvent("newer, cut off")));

        return new TimestampCutoffReadonlyEventStore(backend, backend, past());
    }

    private DomainEventMessage<?> createEventMessage(long sequenceNumber, Object event) {
        return new GenericDomainEventMessage<>("My-1", sequenceNumber, event);
    }
}
