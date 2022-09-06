/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.modelling.command.ConflictingModificationException;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.jupiter.api.*;

import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;

class DefaultConflictResolverTest {

    private EventStore eventStore;
    private DefaultConflictResolver subject;

    @BeforeEach
    void setUp() {
        eventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        eventStore.publish(IntStream.range(0, 10).mapToObj(
                sequenceNumber -> createEvent(AGGREGATE, sequenceNumber, PAYLOAD + sequenceNumber)).collect(toList()));
    }

    @Test
    void detectConflicts() {
        subject = new DefaultConflictResolver(eventStore, AGGREGATE, 5, 9);
        assertThrows(
                ConflictingModificationException.class,
                () -> subject.detectConflicts(Conflicts.payloadTypeOf(String.class)));
    }

    @Test
    void detectNoConflictsWhenPredicateDoesNotMatch() {
        subject = new DefaultConflictResolver(eventStore, AGGREGATE, 5, 9);
        subject.detectConflicts(Conflicts.payloadTypeOf(Long.class));
    }

    @Test
    void detectNoConflictsWithoutUnseenEvents() {
        subject = new DefaultConflictResolver(eventStore, AGGREGATE, 5, 5);
        subject.detectConflicts(Conflicts.payloadTypeOf(String.class));
    }

    @Test
    void ensureConflictsResolvedThrowsExceptionWithoutRegisteredConflicts() {
        subject = new DefaultConflictResolver(eventStore, AGGREGATE, 5, 9);
        assertThrows(ConflictingModificationException.class, subject::ensureConflictsResolved);
    }

    @Test
    void ensureConflictsResolvedDoesNothingWithRegisteredConflicts() {
        subject = new DefaultConflictResolver(eventStore, AGGREGATE, 5, 9);
        subject.detectConflicts(Conflicts.payloadMatching(Long.class::isInstance));
        subject.ensureConflictsResolved();
    }

    @Test
    void conflictingEventsAreAvailableInExceptionBuilder() {
        subject = new DefaultConflictResolver(eventStore, AGGREGATE, 5, 9);
        try {
            subject.detectConflicts(Conflicts.payloadTypeOf(String.class),
                                    c -> new ConflictingModificationException("" + c.unexpectedEvents().size()));
            fail("Expected exception");
        } catch (ConflictingModificationException e) {
            assertEquals("4", e.getMessage());
        }
    }

    @Test
    void conflictResolverProvidingNullExceptionIgnoresConflict() {
        subject = new DefaultConflictResolver(eventStore, AGGREGATE, 5, 9);
        subject.detectConflicts(Conflicts.payloadTypeOf(String.class), c -> null);
    }
}
