/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.common.IdentifierFactory;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.DefaultEventUpcasterChain;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcasterChain;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

import static junit.framework.TestCase.*;
import static org.mockito.Mockito.mock;

/**
 * @author Rene de Waele
 */
public class EventUtilsTest {

    private Serializer serializer;

    @Before
    public void setUp() throws Exception {
        serializer = mock(Serializer.class);
    }

    @Test
    public void testDomainEventStream_lastSequenceNumberEqualToLastProcessedEntry() throws Exception {
        DomainEventStream eventStream = EventUtils
                .upcastAndDeserializeDomainEvents(Stream.of(createEntry(1)), serializer,
                                                  NoOpEventUpcasterChain.INSTANCE, false);
        assertNull(eventStream.getLastSequenceNumber());
        eventStream.forEachRemaining(Objects::requireNonNull);
        assertEquals(Long.valueOf(1L), eventStream.getLastSequenceNumber());
    }

    @Test
    public void testDomainEventStream_lastSequenceNumberEqualToLastProcessedEntryAfterIgnoringLastEntry() throws
            Exception {
        DomainEventStream eventStream = EventUtils
                .upcastAndDeserializeDomainEvents(Stream.of(createEntry(1), createEntry(2), createEntry(3)), serializer,
                                                  new DefaultEventUpcasterChain(e -> Stream.of(e)
                                                          .filter(entry -> entry.getSequenceNumber().get() < 2L)),
                                                  false);
        assertNull(eventStream.getLastSequenceNumber());
        assertTrue(eventStream.hasNext());
        eventStream.forEachRemaining(Objects::requireNonNull);
        assertEquals(Long.valueOf(3L), eventStream.getLastSequenceNumber());
    }

    @Test
    public void testDomainEventStream_lastSequenceNumberEqualToLastProcessedEntryAfterUpcastingToEmptyStream() throws
            Exception {
        DomainEventStream eventStream = EventUtils
                .upcastAndDeserializeDomainEvents(Stream.of(createEntry(1)), serializer,
                                                  new DefaultEventUpcasterChain(e -> Stream.empty()), false);
        assertNull(eventStream.getLastSequenceNumber());
        assertFalse(eventStream.hasNext());
        eventStream.forEachRemaining(Objects::requireNonNull);
        assertEquals(Long.valueOf(1L), eventStream.getLastSequenceNumber());
    }

    private static DomainEventData<?> createEntry(long sequenceNumber) {
        return new GenericDomainEventEntry<>("type", "testAggregate", sequenceNumber,
                                             IdentifierFactory.getInstance().generateIdentifier(), Instant.now(),
                                             String.class.getName(), null, "test", "metadata");
    }

}