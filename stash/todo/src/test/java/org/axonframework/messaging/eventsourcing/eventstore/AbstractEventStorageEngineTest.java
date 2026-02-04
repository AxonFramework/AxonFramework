/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventsourcing.eventstore;

import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.DomainEventTestUtils;
import org.axonframework.messaging.eventsourcing.eventstore.jdbc.LegacyJdbcEventStorageEngine;
import org.axonframework.messaging.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.modelling.ConcurrencyException;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.conversion.upcasting.event.EventUpcaster;
import org.junit.jupiter.api.*;

import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Abstract test class used to create tests for the {@link LegacyJdbcEventStorageEngine}.
 * <p>
 * Methods are public so they can be overridden by {@link LegacyEventStorageEngine} implementation test cases in
 * different repository, like the [Mongo Extension](https://github.com/AxonFramework/extension-mongo).
 *
 * @author Rene de Waele
 */
public abstract class AbstractEventStorageEngineTest<E extends AbstractEventStorageEngine, EB extends AbstractEventStorageEngine.Builder>
        extends EventStorageEngineTest {

    private AbstractEventStorageEngine testSubject;

    @Test
    public void uniqueKeyConstraintOnFirstEventIdentifierThrowsAggregateIdentifierAlreadyExistsException() {
        assertThrows(
                AggregateStreamCreationException.class,
                () -> testSubject.appendEvents(DomainEventTestUtils.createDomainEvent("id",
                                                                                      DomainEventTestUtils.AGGREGATE,
                                                                                      0),
                                               DomainEventTestUtils.createDomainEvent("id", "otherAggregate", 0))
        );
    }

    @Test
    public void uniqueKeyConstraintOnEventIdentifier() {
        assertThrows(
                ConcurrencyException.class,
                () -> testSubject.appendEvents(DomainEventTestUtils.createDomainEvent("id",
                                                                                      DomainEventTestUtils.AGGREGATE,
                                                                                      1),
                                               DomainEventTestUtils.createDomainEvent("id", "otherAggregate", 1))
        );
    }

    @Test
    @Disabled("TODO #3597")
    public void storeAndLoadEventsWithUpcaster() {
        EventUpcaster mockUpcasterChain = mock(EventUpcaster.class);
        //noinspection unchecked
        when(mockUpcasterChain.upcast(isA(Stream.class))).thenAnswer(invocation -> {
            Stream<?> inputStream = (Stream<?>) invocation.getArguments()[0];
            return inputStream.flatMap(e -> Stream.of(e, e));
        });
        //noinspection unchecked
//        testSubject = createEngine(engineBuilder -> (EB) engineBuilder.upcasterChain(mockUpcasterChain));

        testSubject.appendEvents(DomainEventTestUtils.createDomainEvents(4));
        List<DomainEventMessage> upcastedEvents = testSubject.readEvents(DomainEventTestUtils.AGGREGATE).asStream()
                                                             .collect(toList());
        assertEquals(8, upcastedEvents.size());

        Iterator<DomainEventMessage> iterator = upcastedEvents.iterator();
        while (iterator.hasNext()) {
            DomainEventMessage event1 = iterator.next(), event2 = iterator.next();
            assertEquals(event1.getAggregateIdentifier(), event2.getAggregateIdentifier());
            assertEquals(event1.getSequenceNumber(), event2.getSequenceNumber());
            assertEquals(event1.payload(), event2.payload());
            assertEquals(event1.metadata(), event2.metadata());
        }
    }

    @Test
    public void storeDuplicateFirstEventWithExceptionTranslatorThrowsAggregateIdentifierAlreadyExistsException() {
        assertThrows(
                AggregateStreamCreationException.class,
                () -> testSubject.appendEvents(DomainEventTestUtils.createDomainEvent(0),
                                               DomainEventTestUtils.createDomainEvent(0))
        );
    }

    @Test
    public void storeDuplicateEventWithExceptionTranslator() {
        assertThrows(
                ConcurrencyException.class,
                () -> testSubject.appendEvents(DomainEventTestUtils.createDomainEvent(1),
                                               DomainEventTestUtils.createDomainEvent(1))
        );
    }

    @Test
    public void storeDuplicateEventWithoutExceptionResolver() {
        //noinspection unchecked
        testSubject = createEngine(engineBuilder -> (EB) engineBuilder.persistenceExceptionResolver(e -> false));
        assertThrows(
                EventStoreException.class,
                () -> testSubject.appendEvents(DomainEventTestUtils.createDomainEvent(0),
                                               DomainEventTestUtils.createDomainEvent(0))
        );
    }

    @Test
    public void snapshotFilterAllowsSnapshots() {
        SnapshotFilter allowAll = SnapshotFilter.allowAll();

        //noinspection unchecked
//        testSubject = createEngine(builder -> (EB) builder.snapshotFilter(allowAll));

        testSubject.storeSnapshot(DomainEventTestUtils.createDomainEvent(1));
        assertTrue(testSubject.readSnapshot(DomainEventTestUtils.AGGREGATE).isPresent());
    }

    @Test
    @Disabled("TODO #3105")
    public void snapshotFilterRejectsSnapshots() {
        SnapshotFilter rejectAll = SnapshotFilter.rejectAll();

        //noinspection unchecked
//        testSubject = createEngine(builder -> (EB) builder.snapshotFilter(rejectAll));

        testSubject.storeSnapshot(DomainEventTestUtils.createDomainEvent(1));
        assertFalse(testSubject.readSnapshot(DomainEventTestUtils.AGGREGATE).isPresent());
    }

    @Test
    @Disabled("TODO #3105")
    public void snapshotFilterRejectsSnapshotsOnCombinedFilter() {
        SnapshotFilter combinedFilter = SnapshotFilter.allowAll().combine(SnapshotFilter.rejectAll());

        //noinspection unchecked
//        testSubject = createEngine(builder -> (EB) builder.snapshotFilter(combinedFilter));

        testSubject.storeSnapshot(DomainEventTestUtils.createDomainEvent(1));
        assertFalse(testSubject.readSnapshot(DomainEventTestUtils.AGGREGATE).isPresent());
    }

    protected void setTestSubject(AbstractEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
    }

    protected E createEngine() {
        return createEngine(builder -> builder);
    }

    protected abstract E createEngine(UnaryOperator<EB> customization);
}
