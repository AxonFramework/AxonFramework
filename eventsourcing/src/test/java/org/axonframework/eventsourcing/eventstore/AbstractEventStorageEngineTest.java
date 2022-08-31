/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.junit.jupiter.api.*;

import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Abstract test class used to create tests for the {@link org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine}
 * and {@link org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine}.
 * <p>
 * Methods are public so they can be overridden by {@link EventStorageEngine} implementation test cases in different
 * repository, like the [Mongo Extension](https://github.com/AxonFramework/extension-mongo).
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
                () -> testSubject.appendEvents(createEvent("id", AGGREGATE, 0), createEvent("id", "otherAggregate", 0))
        );
    }

    @Test
    public void uniqueKeyConstraintOnEventIdentifier() {
        assertThrows(
                ConcurrencyException.class,
                () -> testSubject.appendEvents(createEvent("id", AGGREGATE, 1), createEvent("id", "otherAggregate", 1))
        );
    }

    @Test
    public void storeAndLoadEventsWithUpcaster() {
        EventUpcaster mockUpcasterChain = mock(EventUpcaster.class);
        //noinspection unchecked
        when(mockUpcasterChain.upcast(isA(Stream.class))).thenAnswer(invocation -> {
            Stream<?> inputStream = (Stream<?>) invocation.getArguments()[0];
            return inputStream.flatMap(e -> Stream.of(e, e));
        });
        //noinspection unchecked
        testSubject = createEngine(engineBuilder -> (EB) engineBuilder.upcasterChain(mockUpcasterChain));

        testSubject.appendEvents(createEvents(4));
        List<DomainEventMessage<?>> upcastedEvents = testSubject.readEvents(AGGREGATE).asStream().collect(toList());
        assertEquals(8, upcastedEvents.size());

        Iterator<DomainEventMessage<?>> iterator = upcastedEvents.iterator();
        while (iterator.hasNext()) {
            DomainEventMessage<?> event1 = iterator.next(), event2 = iterator.next();
            assertEquals(event1.getAggregateIdentifier(), event2.getAggregateIdentifier());
            assertEquals(event1.getSequenceNumber(), event2.getSequenceNumber());
            assertEquals(event1.getPayload(), event2.getPayload());
            assertEquals(event1.getMetaData(), event2.getMetaData());
        }
    }

    @Test
    public void storeDuplicateFirstEventWithExceptionTranslatorThrowsAggregateIdentifierAlreadyExistsException() {
        assertThrows(
                AggregateStreamCreationException.class,
                () -> testSubject.appendEvents(createEvent(0), createEvent(0))
        );
    }

    @Test
    public void storeDuplicateEventWithExceptionTranslator() {
        assertThrows(
                ConcurrencyException.class,
                () -> testSubject.appendEvents(createEvent(1), createEvent(1))
        );
    }

    @Test
    public void storeDuplicateEventWithoutExceptionResolver() {
        //noinspection unchecked
        testSubject = createEngine(engineBuilder -> (EB) engineBuilder.persistenceExceptionResolver(e -> false));
        assertThrows(
                EventStoreException.class,
                () -> testSubject.appendEvents(createEvent(0), createEvent(0))
        );
    }

    @Test
    public void snapshotFilterAllowsSnapshots() {
        SnapshotFilter allowAll = SnapshotFilter.allowAll();

        //noinspection unchecked
        testSubject = createEngine(builder -> (EB) builder.snapshotFilter(allowAll));

        testSubject.storeSnapshot(createEvent(1));
        assertTrue(testSubject.readSnapshot(AGGREGATE).isPresent());
    }

    @Test
    public void snapshotFilterRejectsSnapshots() {
        SnapshotFilter rejectAll = SnapshotFilter.rejectAll();

        //noinspection unchecked
        testSubject = createEngine(builder -> (EB) builder.snapshotFilter(rejectAll));

        testSubject.storeSnapshot(createEvent(1));
        assertFalse(testSubject.readSnapshot(AGGREGATE).isPresent());
    }

    @Test
    public void snapshotFilterRejectsSnapshotsOnCombinedFilter() {
        SnapshotFilter combinedFilter = SnapshotFilter.allowAll().combine(SnapshotFilter.rejectAll());

        //noinspection unchecked
        testSubject = createEngine(builder -> (EB) builder.snapshotFilter(combinedFilter));

        testSubject.storeSnapshot(createEvent(1));
        assertFalse(testSubject.readSnapshot(AGGREGATE).isPresent());
    }

    protected void setTestSubject(AbstractEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
    }

    protected E createEngine() {
        return createEngine(builder -> builder);
    }

    protected abstract E createEngine(UnaryOperator<EB> customization);
}
