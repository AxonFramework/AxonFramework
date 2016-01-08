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
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Knut-Olav Hoven
 */
public class VolatileEventStoreTest {

    @Test
    public void readEvents_givenNoEvents() {
        VolatileEventStore es = new VolatileEventStore();

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenEvents_ofTwoAggregates() {
        VolatileEventStore es = new VolatileEventStore();

        String aggregateId = "My-1";
        es.appendEvents(createEventMessage(aggregateId, 0, new MyEvent(1)),
                        createEventMessage(aggregateId, 1, new MyEvent(2)));

        aggregateId = "My-2";
        es.appendEvents(createEventMessage(aggregateId, 0, new MyEvent(21)),
                createEventMessage(aggregateId, 1, new MyEvent(22)));

        DomainEventStream readEvents = es.readEvents("My-1");

        assertThat(readEvents.hasNext()).isTrue();
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(0L);
        assertThat(readEvents.next().getSequenceNumber()).isEqualTo(1L);
        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void visitEvents_givenNoEvents() {
        VolatileEventStore es = new VolatileEventStore();

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).isEmpty();
    }

    @Test
    public void visitEvents_givenEvents() {
        VolatileEventStore es = new VolatileEventStore();
        String aggregateId = "My-1";
        es.appendEvents(createEventMessage(aggregateId, 0, new MyEvent(1)),
                        createEventMessage(aggregateId, 1, new MyEvent(2)));

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(2);
        assertThat(visitor.visited().get(1).getSequenceNumber()).isEqualTo(1L);
    }

    private DomainEventMessage<?> createEventMessage(String aggregateId, long sequenceNumber, Object event) {
        return new GenericDomainEventMessage<>(aggregateId, sequenceNumber, event);
    }
}
