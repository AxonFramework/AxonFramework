/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.UnknownSerializedType;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rene de Waele
 */
public class JdbcEventStorageEngineTest extends BatchingEventStorageEngineTest {

    private JDBCDataSource dataSource;
    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;
    private JdbcEventStorageEngine testSubject;

    @BeforeEach
    public void setUp() throws SQLException {
        dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");
        defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
        setTestSubject(testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver,
                                                  new EventSchema(), byte[].class, HsqlEventTableFactory.INSTANCE));
    }

    @Test
    public void testStoreTwoExactSameSnapshots() {
        testSubject.storeSnapshot(createEvent(1));
        testSubject.storeSnapshot(createEvent(1));
    }

    @Test
    public void testLoadLastSequenceNumber() {
        String aggregateId = UUID.randomUUID().toString();
        testSubject.appendEvents(createEvent(aggregateId, 0), createEvent(aggregateId, 1));
        assertEquals(1L, (long) testSubject.lastSequenceNumberFor(aggregateId).orElse(-1L));
        assertFalse(testSubject.lastSequenceNumberFor("inexistent").isPresent());
    }

    @Test
    @DirtiesContext
    public void testCustomSchemaConfig() {
        setTestSubject(testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver,
                                                  EventSchema.builder()
                                                             .eventTable("CustomDomainEvent")
                                                             .payloadColumn("eventData").build(), String.class,
                                                  new HsqlEventTableFactory() {
                                                      @Override
                                                      protected String payloadType() {
                                                          return "LONGVARCHAR";
                                                      }
                                                  }));
        testStoreAndLoadEvents();
    }

    @Test
    @DirtiesContext
    public void testCustomSchemaConfigTimestampColumn() {
        setTestSubject(testSubject = createTimestampEngine(new HsqlEventTableFactory() {
            @Override
            protected String timestampType() {
                return "timestamp";
            }
        }));
        testStoreAndLoadEvents();
    }

    @Test
    public void testGapsForVeryOldEventsAreNotIncluded() throws SQLException {
        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(1, ChronoUnit.HOURS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-1), createEvent(0));

        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(2, ChronoUnit.MINUTES), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-2), createEvent(1));

        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(50, ChronoUnit.SECONDS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-3), createEvent(2));

        GenericEventMessage.clock = Clock.fixed(Clock.systemUTC().instant(), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-4), createEvent(3));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE sequenceNumber < 0").executeUpdate();
        }

        testSubject.fetchTrackedEvents((TrackingToken) null, 100).stream()
                   .map(i -> (GapAwareTrackingToken) i.trackingToken())
                   .forEach(i -> assertTrue(i.getGaps().size() <= 2));
    }

    @DirtiesContext
    @Test
    public void testOldGapsAreRemovedFromProvidedTrackingToken() throws SQLException {
        testSubject.setGapTimeout(50001);
        testSubject.setGapCleaningThreshold(50);
        Instant now = Clock.systemUTC().instant();
        GenericEventMessage.clock = Clock.fixed(now.minus(1, ChronoUnit.HOURS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-1), createEvent(0)); // index 0 and 1
        GenericEventMessage.clock = Clock.fixed(now.minus(2, ChronoUnit.MINUTES), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-2), createEvent(1)); // index 2 and 3
        GenericEventMessage.clock = Clock.fixed(now.minus(50, ChronoUnit.SECONDS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-3), createEvent(2)); // index 4 and 5
        GenericEventMessage.clock = Clock.fixed(now, Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-4), createEvent(3)); // index 6 and 7

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE sequenceNumber < 0").executeUpdate();
        }

        List<Long> gaps = LongStream.range(-50, 6)
                                    .filter(i -> i != 1L && i != 3L && i != 5)
                                    .boxed()
                                    .collect(Collectors.toList());
        List<? extends TrackedEventData<?>> events =
                testSubject.fetchTrackedEvents(GapAwareTrackingToken.newInstance(6, gaps), 100);
        assertEquals(1, events.size());
        assertEquals(4L, (long) ((GapAwareTrackingToken) events.get(0).trackingToken()).getGaps().first());
    }

    @Test
    public void testEventsWithUnknownPayloadTypeDoNotResultInError() throws SQLException, InterruptedException {
        String expectedPayloadOne = "Payload3";
        String expectedPayloadTwo = "Payload4";

        int testBatchSize = 2;
        testSubject = createEngine(defaultPersistenceExceptionResolver, new EventSchema(), testBatchSize);
        EmbeddedEventStore testEventStore = EmbeddedEventStore.builder().storageEngine(testSubject).build();

        testSubject.appendEvents(createEvent(AGGREGATE, 1, "Payload1"),
                                 createEvent(AGGREGATE, 2, "Payload2"));
        // Update events which will be part of the first batch to an unknown payload type
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("UPDATE DomainEventEntry e SET e.payloadType = 'unknown'")
                .executeUpdate();
        }
        testSubject.appendEvents(createEvent(AGGREGATE, 3, expectedPayloadOne),
                                 createEvent(AGGREGATE, 4, expectedPayloadTwo));

        List<String> eventStorageEngineResult = testSubject.readEvents(null, false)
                                                           .filter(m -> m.getPayload() instanceof String)
                                                           .map(m -> (String) m.getPayload())
                                                           .collect(toList());
        assertEquals(Arrays.asList(expectedPayloadOne, expectedPayloadTwo), eventStorageEngineResult);

        TrackingEventStream eventStoreResult = testEventStore.openStream(null);
        assertTrue(eventStoreResult.hasNextAvailable());
        assertEquals(UnknownSerializedType.class, eventStoreResult.nextAvailable().getPayloadType());
        assertEquals(UnknownSerializedType.class, eventStoreResult.nextAvailable().getPayloadType());
        assertEquals(expectedPayloadOne, eventStoreResult.nextAvailable().getPayload());
        assertEquals(expectedPayloadTwo, eventStoreResult.nextAvailable().getPayload());
    }

    @Test
    public void testStreamCrossesConsecutiveGapsOfMoreThanBatchSuccessfully() throws SQLException {
        testSubject = createEngine(defaultPersistenceExceptionResolver, new EventSchema(), 10);
        testSubject.appendEvents(createEvents(100));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE globalIndex >= 20 and globalIndex < 40")
                .executeUpdate();
        }

        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(null, false);
        List<? extends TrackedEventMessage<?>> actualEvents = actual.collect(toList());
        assertEquals(80, actualEvents.size());
    }

    @Test
    public void testStreamDoesNotCrossExtendedGapWhenDisabled() throws SQLException {
        testSubject = JdbcEventStorageEngine.builder().upcasterChain(NoOpEventUpcaster.INSTANCE)
                                            .batchSize(10)
                                            .connectionProvider(dataSource::getConnection)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .schema(new EventSchema())
                                            .dataType(byte[].class)
                                            .extendedGapCheckEnabled(false)
                                            .build();

        try {
            Connection connection = dataSource.getConnection();
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            testSubject.createSchema(HsqlEventTableFactory.INSTANCE);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        testSubject.appendEvents(createEvents(100));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE globalIndex >= 20 and globalIndex < 40")
                .executeUpdate();
        }

        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(null, false);
        List<? extends TrackedEventMessage<?>> actualEvents = actual.collect(toList());
        assertEquals(20, actualEvents.size());
    }

    @Test
    public void testStreamCrossesInitialConsecutiveGapsOfMoreThanBatchSuccessfully() throws SQLException {
        testSubject = createEngine(defaultPersistenceExceptionResolver, new EventSchema(), 10);
        testSubject.appendEvents(createEvents(100));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE globalIndex < 20")
                .executeUpdate();
        }

        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(null, false);
        List<? extends TrackedEventMessage<?>> actualEvents = actual.collect(toList());
        assertEquals(80, actualEvents.size());
    }

    @Test
    public void testLoadSnapshotIfMatchesPredicate() {
        Predicate<DomainEventData<?>> acceptAll = i -> true;

        setTestSubject(testSubject = createEngine(acceptAll));

        testSubject.storeSnapshot(createEvent(1));
        assertTrue(testSubject.readSnapshot(AGGREGATE).isPresent());
    }

    @Test
    public void testDoNotLoadSnapshotIfNotMatchingPredicate() {
        Predicate<DomainEventData<?>> rejectAll = i -> false;

        setTestSubject(testSubject = createEngine(rejectAll));

        testSubject.storeSnapshot(createEvent(1));
        assertFalse(testSubject.readSnapshot(AGGREGATE).isPresent());
    }

    @Test
    public void testReadEventsForAggregateReturnsTheCompleteStream() {
        testSubject = createEngine(defaultPersistenceExceptionResolver, new EventSchema(), 10);

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        DomainEventMessage<String> testEventThree = createEvent(2);
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);

        testSubject.appendEvents(testEventOne, testEventTwo, testEventThree, testEventFour, testEventFive);

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertEquals(5, result.size());
        assertEquals(0, result.get(0).getSequenceNumber());
        assertEquals(1, result.get(1).getSequenceNumber());
        assertEquals(2, result.get(2).getSequenceNumber());
        assertEquals(3, result.get(3).getSequenceNumber());
        assertEquals(4, result.get(4).getSequenceNumber());
    }

    @Test
    public void testReadEventsForAggregateWithGapsReturnsTheCompleteStream() {
        testSubject = createEngine(defaultPersistenceExceptionResolver, new EventSchema(), 10);

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        // Event with sequence number 2 is missing -> the gap
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);

        testSubject.appendEvents(testEventOne, testEventTwo, testEventFour, testEventFive);

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertEquals(4, result.size());
        assertEquals(0, result.get(0).getSequenceNumber());
        assertEquals(1, result.get(1).getSequenceNumber());
        assertEquals(3, result.get(2).getSequenceNumber());
        assertEquals(4, result.get(3).getSequenceNumber());
    }

    @Test
    public void testReadEventsForAggregateWithEventsExceedingOneBatchReturnsTheCompleteStream() {
        // Set batch size to 5, so that the number of events exceeds at least one batch
        int batchSize = 5;
        testSubject = createEngine(defaultPersistenceExceptionResolver, new EventSchema(), batchSize);

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        DomainEventMessage<String> testEventThree = createEvent(2);
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);
        DomainEventMessage<String> testEventSix = createEvent(5);
        DomainEventMessage<String> testEventSeven = createEvent(6);
        DomainEventMessage<String> testEventEight = createEvent(7);

        testSubject.appendEvents(
                testEventOne, testEventTwo, testEventThree, testEventFour, testEventFive, testEventSix, testEventSeven,
                testEventEight
        );

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertEquals(8, result.size());
        assertEquals(0, result.get(0).getSequenceNumber());
        assertEquals(1, result.get(1).getSequenceNumber());
        assertEquals(2, result.get(2).getSequenceNumber());
        assertEquals(3, result.get(3).getSequenceNumber());
        assertEquals(4, result.get(4).getSequenceNumber());
        assertEquals(5, result.get(5).getSequenceNumber());
        assertEquals(6, result.get(6).getSequenceNumber());
        assertEquals(7, result.get(7).getSequenceNumber());
    }

    @Test
    public void testReadEventsForAggregateWithEventsExceedingOneBatchAndGapsReturnsTheCompleteStream() {
        // Set batch size to 5, so that the number of events exceeds at least one batch
        int batchSize = 5;
        testSubject = createEngine(defaultPersistenceExceptionResolver, new EventSchema(), batchSize);

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        // Event with sequence number 2 is missing -> the gap
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);
        DomainEventMessage<String> testEventSix = createEvent(5);
        DomainEventMessage<String> testEventSeven = createEvent(6);
        DomainEventMessage<String> testEventEight = createEvent(7);

        testSubject.appendEvents(
                testEventOne, testEventTwo, testEventFour, testEventFive, testEventSix, testEventSeven,
                testEventEight
        );

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertEquals(7, result.size());
        assertEquals(0, result.get(0).getSequenceNumber());
        assertEquals(1, result.get(1).getSequenceNumber());
        assertEquals(3, result.get(2).getSequenceNumber());
        assertEquals(4, result.get(3).getSequenceNumber());
        assertEquals(5, result.get(4).getSequenceNumber());
        assertEquals(6, result.get(5).getSequenceNumber());
        assertEquals(7, result.get(6).getSequenceNumber());
    }

    @Override
    protected AbstractEventStorageEngine createEngine(EventUpcaster upcasterChain) {
        return createEngine(upcasterChain, defaultPersistenceExceptionResolver, new EventSchema(), byte[].class,
                            HsqlEventTableFactory.INSTANCE);
    }

    @Override
    protected AbstractEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        return createEngine(NoOpEventUpcaster.INSTANCE,
                            persistenceExceptionResolver,
                            new EventSchema(),
                            byte[].class,
                            HsqlEventTableFactory.INSTANCE);
    }

    private JdbcEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                PersistenceExceptionResolver persistenceExceptionResolver,
                                                EventSchema eventSchema,
                                                Class<?> dataType,
                                                EventTableFactory tableFactory) {
        return createEngine(upcasterChain,
                            persistenceExceptionResolver,
                            snapshot -> true,
                            eventSchema,
                            dataType,
                            tableFactory,
                            100);
    }

    private JdbcEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver,
                                                EventSchema eventSchema,
                                                int batchSize) {
        return createEngine(NoOpEventUpcaster.INSTANCE,
                            persistenceExceptionResolver,
                            snapshot -> true,
                            eventSchema,
                            byte[].class,
                            HsqlEventTableFactory.INSTANCE,
                            batchSize);
    }

    private JdbcEventStorageEngine createEngine(Predicate<? super DomainEventData<?>> snapshotFilter) {
        return createEngine(NoOpEventUpcaster.INSTANCE,
                            defaultPersistenceExceptionResolver,
                            snapshotFilter,
                            new EventSchema(),
                            byte[].class,
                            HsqlEventTableFactory.INSTANCE,
                            100);
    }

    private JdbcEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                PersistenceExceptionResolver persistenceExceptionResolver,
                                                Predicate<? super DomainEventData<?>> snapshotFilter,
                                                EventSchema eventSchema,
                                                Class<?> dataType,
                                                EventTableFactory tableFactory,
                                                int batchSize) {
        JdbcEventStorageEngine result = JdbcEventStorageEngine.builder()
                                                              .upcasterChain(upcasterChain)
                                                              .persistenceExceptionResolver(persistenceExceptionResolver)
                                                              .snapshotFilter(snapshotFilter)
                                                              .batchSize(batchSize)
                                                              .connectionProvider(dataSource::getConnection)
                                                              .transactionManager(NoTransactionManager.INSTANCE)
                                                              .schema(eventSchema)
                                                              .dataType(dataType)
                                                              .build();

        return doCreateTables(tableFactory, result);
    }

    private JdbcEventStorageEngine createTimestampEngine(EventTableFactory eventTableFactory) {
        JdbcEventStorageEngine.Builder builder = JdbcEventStorageEngine.builder()
                                                                       .connectionProvider(dataSource::getConnection)
                                                                       .transactionManager(NoTransactionManager.INSTANCE);

        JdbcEventStorageEngine result = new JdbcEventStorageEngine(builder) {
            @Override
            protected Object readTimeStamp(ResultSet resultSet, String columnName) throws SQLException {
                Timestamp ts = resultSet.getTimestamp(columnName);
                return ts.toInstant();
            }

            @Override
            protected void writeTimestamp(PreparedStatement preparedStatement, int position, Instant timestamp)
                    throws SQLException {
                preparedStatement.setTimestamp(position, new Timestamp(timestamp.toEpochMilli()));
            }
        };

        return doCreateTables(eventTableFactory, result);
    }

    private JdbcEventStorageEngine doCreateTables(EventTableFactory eventTableFactory, JdbcEventStorageEngine result) {
        try {
            Connection connection = dataSource.getConnection();
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            result.createSchema(eventTableFactory);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
