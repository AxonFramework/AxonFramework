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
import org.junit.Test;

/**
 * @author Knut-Olav Hoven
 */
public class VolatileEventStoreTest {

    @Test
    public void readEvents_givenNoEvents() {
        VolatileEventStore es = new VolatileEventStore();

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

        assertThat(readEvents.hasNext()).isFalse();
    }

    @Test
    public void readEvents_givenEvents_ofTwoAggregates() {
        VolatileEventStore es = new VolatileEventStore();

        EventContainer ec1 = new EventContainer("My-1");
        ec1.addEvent(MetaData.emptyInstance(), new MyEvent(1));
        ec1.addEvent(MetaData.emptyInstance(), new MyEvent(2));
        es.appendEvents("MyAggregate", ec1.getEventStream());

        EventContainer ec2 = new EventContainer("My-2");
        ec2.addEvent(MetaData.emptyInstance(), new MyEvent(21));
        ec2.addEvent(MetaData.emptyInstance(), new MyEvent(22));
        es.appendEvents("MyAggregate", ec2.getEventStream());

        DomainEventStream readEvents = es.readEvents("MyAggregate", "My-1");

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
        EventContainer ec = new EventContainer("My-1");

        ec.addEvent(MetaData.emptyInstance(), new MyEvent(1));
        ec.addEvent(MetaData.emptyInstance(), new MyEvent(2));

        es.appendEvents("MyAggregate", ec.getEventStream());

        CapturingEventVisitor visitor = new CapturingEventVisitor();

        es.visitEvents(visitor);

        assertThat(visitor.visited()).hasSize(2);
        assertThat(visitor.visited().get(1).getSequenceNumber()).isEqualTo(1L);
    }
}
