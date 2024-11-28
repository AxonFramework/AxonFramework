/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.junit.jupiter.api.Assertions.*;

class ConcatenatingDomainEventStreamTest {

    private DomainEventMessage<String> event1;
    private DomainEventMessage<String> event2;
    private DomainEventMessage<String> event3;
    private DomainEventMessage<String> event4;
    private DomainEventMessage<String> event5;

    @BeforeEach
    void setUp() {
        event1 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 0,
                                                 dottedName("test.event"), "Mock contents 1");
        event2 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 1,
                                                 dottedName("test.event"), "Mock contents 2");
        event3 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 2,
                                                 dottedName("test.event"), "Mock contents 3");
        event4 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 3,
                                                 dottedName("test.event"), "Mock contents 4");
        event5 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 4,
                                                 dottedName("test.event"), "Mock contents 5");
    }

    @Test
    void forEachRemaining() {
        List<DomainEventMessage<String>> expectedMessages = Arrays.asList(event1, event2, event3, event4, event5);

        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1, event2), // Initial stream - add all elements
                DomainEventStream.of(event3, event4), // No overlap with previous stream - add all elements
                DomainEventStream.of(event3, event4), // Complete overlap with previous stream - add no elements
                DomainEventStream.of(event4, event5) // Partial overlap with previous stream - add some elements
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertEquals(expectedMessages, actualMessages);
    }

    @Test
    void forEachRemainingKeepsDuplicateSequenceIdEventsInSameStream() {
        List<DomainEventMessage<String>> expectedMessages =
                Arrays.asList(event1, event1, event2, event3, event4, event4, event5);

        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1, event1, event2),
                DomainEventStream.of(event2, event3),
                DomainEventStream.empty(),
                DomainEventStream.of(event3, event3),
                DomainEventStream.of(event3, event4, event4),
                DomainEventStream.of(event4, event5)
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertEquals(expectedMessages, actualMessages);
    }

    @Test
    void concatSkipsDuplicateEvents() {
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

    @Test
    public void concatDoesNotSkipDuplicateSequencesInSameStream() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1, event1, event2),
                                                                      DomainEventStream.of(event2, event2, event3),
                                                                      DomainEventStream.of(event3, event4));

        assertTrue(concat.hasNext());
        assertSame(event1.getPayload(), concat.next().getPayload());
        assertSame(event1.getPayload(), concat.next().getPayload());
        assertSame(event2.getPayload(), concat.next().getPayload());
        assertSame(event3.getPayload(), concat.next().getPayload());
        assertSame(event4.getPayload(), concat.next().getPayload());
        assertFalse(concat.hasNext());
    }


    @Test
    void lastKnownSequenceReturnsTheLastEventItsSequence() {
        DomainEventStream lastStream = DomainEventStream.of(event4, event5);
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1),
                                                                      DomainEventStream.of(event2, event3),
                                                                      lastStream);

        assertTrue(concat.hasNext());

        assertSame(event1.getPayload(), concat.next().getPayload());
        assertSame(event2.getPayload(), concat.next().getPayload());
        assertSame(event3.getPayload(), concat.next().getPayload());
        assertSame(event4.getPayload(), concat.next().getPayload());
        assertSame(event5.getPayload(), concat.next().getPayload());

        assertFalse(concat.hasNext());

        assertEquals(lastStream.getLastSequenceNumber(), concat.getLastSequenceNumber());
    }

    @Test
    void lastKnownSequenceReturnsTheLastEventItsSequenceEventIfEventsHaveGaps() {
        DomainEventStream lastStream = DomainEventStream.of(event4, event5);
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1, event3),
                                                                      lastStream);

        assertTrue(concat.hasNext());

        assertSame(event1.getPayload(), concat.next().getPayload());
        assertSame(event3.getPayload(), concat.next().getPayload());
        assertSame(event4.getPayload(), concat.next().getPayload());
        assertSame(event5.getPayload(), concat.next().getPayload());

        assertFalse(concat.hasNext());

        assertEquals(lastStream.getLastSequenceNumber(), concat.getLastSequenceNumber());
    }

    @Test
    void lastSequenceNumberWhenLastEventIsFilteredOut() {
        // the last sequence number is the one from the event that is filtered out, e.g. when using upcasters
        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(
                        Stream.of(event1, event2).filter(x -> x != event2),
                        () -> event2.getSequenceNumber()));

        assertSame(event1, concat.next());
        assertFalse(concat.hasNext());
        assertEquals(event2.getSequenceNumber(), concat.getLastSequenceNumber());
    }

    @Test
    void maxLastSequenceNumberFromDelegates() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1, event3),
                DomainEventStream.of(event2));

        assertSame(event1, concat.next());
        assertSame(event3, concat.next());
        assertFalse(concat.hasNext());

        assertEquals(event3.getSequenceNumber(), concat.getLastSequenceNumber());
    }

    @Test
    void nullSequenceNumberFromDelegates_NullPointer() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1),
                DomainEventStream.of(Stream.empty(), () -> null));

        assertEquals(event1.getSequenceNumber(), concat.getLastSequenceNumber());
    }
}
