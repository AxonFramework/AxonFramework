/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ConcatenatingDomainEventStreamTest {

    private DomainEventMessage event1;
    private DomainEventMessage event2;
    private DomainEventMessage event3;
    private DomainEventMessage event4;

    @Before
    public void setUp() throws Exception {
        event1 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), (long) 0,
                                                 "Mock contents 1", MetaData.emptyInstance());
        event2 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), (long) 1,
                                                 "Mock contents 2", MetaData.emptyInstance());
        event3 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), (long) 2,
                                                 "Mock contents 3", MetaData.emptyInstance());
        event4 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), (long) 3,
                                                 "Mock contents 4", MetaData.emptyInstance());
    }

    @Test
    public void testForEachRemaining() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1, event2),
                                                                      DomainEventStream.of(event2, event3),
                                                                      DomainEventStream.of(event3),
                                                                      DomainEventStream.of(event3, event4),
                                                                      DomainEventStream.of(event4));
        List<DomainEventMessage<?>> messages = new ArrayList<>();
        concat.forEachRemaining(messages::add);

        assertEquals(Arrays.asList(event1, event2, event3, event4), messages);
    }

    @Test
    public void testConcatSkipsDuplicateEvents() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1, event2),
                                                                      DomainEventStream.of(event2, event3),
                                                                      DomainEventStream.of(event3, event4));

        assertTrue(concat.hasNext());
        assertSame(event1.getPayload(), concat.next().getPayload());
        assertSame(event2.getPayload(), concat.next().getPayload());

        assertSame(event3.getPayload(), concat.peek().getPayload());

        assertSame(event3.getPayload(), concat.next().getPayload());

        assertSame(event4.getPayload(), concat.peek().getPayload());
        assertSame(event4.getPayload(), concat.next().getPayload());
        assertFalse(concat.hasNext());
    }


}
