/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.jdbc.statements.JdbcEventStorageEngineStatements;
import org.axonframework.eventsourcing.eventstore.jdbc.statements.ReadEventDataForAggregateStatementBuilder;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.eventsourcing.utils.TestSerializer;
import org.axonframework.serialization.UnknownSerializedType;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.AGGREGATE;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test class validating the {@link JdbcEventStorageEngine}.
 *
 * @author Rene de Waele
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
class JdbcEventStorageEngineTest
        extends BatchingEventStorageEngineTest<JdbcEventStorageEngine, JdbcEventStorageEngine.Builder> {

    private JDBCDataSource dataSource;
    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;
    private JdbcEventStorageEngine testSubject;
    private ReadEventDataForAggregateStatementBuilder readForAggregateStatementBuilder;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");
        defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
        //noinspection Convert2Lambda,Anonymous2MethodRef
        readForAggregateStatementBuilder = spy(new ReadEventDataForAggregateStatementBuilder() {
            @Override
            public PreparedStatement build(Connection connection, EventSchema schema, String identifier, long firstSequenceNumber, int batchSize) throws SQLException {
                return JdbcEventStorageEngineStatements.readEventDataForAggregate(connection, schema, identifier, firstSequenceNumber, batchSize);
            }
        });
        setTestSubject(testSubject = createEngine(b -> b.readEventDataForAggregate(readForAggregateStatementBuilder)));
    }

    @Test
    void testLoadLargeAmountOfEventsFromAggregateStream() {
        super.loadLargeAmountOfEventsFromAggregateStream();

        try {
            verify(readForAggregateStatementBuilder, times(6)).build(any(), any(), eq(AGGREGATE), anyLong(), anyInt());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void loadLargeAmountOfEventsFromAggregateStream_WithCustomFinalBatchPredicate() throws SQLException {
        setTestSubject(testSubject = createEngine(b -> b.finalAggregateBatchPredicate(l -> l.size() < 50)
                                                        .readEventDataForAggregate(readForAggregateStatementBuilder)));

        super.loadLargeAmountOfEventsFromAggregateStream();

        verify(readForAggregateStatementBuilder, times(4)).build(any(), any(), eq(AGGREGATE), anyLong(), anyInt());
    }

    @Test
    void storeTwoExactSameSnapshots() {
        testSubject.storeSnapshot(createEvent(1));
        testSubject.storeSnapshot(createEvent(1));
    }

    @Test
    void loadLastSequenceNumber() {
        String aggregateId = UUID.randomUUID().toString();
        testSubject.appendEvents(createEvent(aggregateId, 0), createEvent(aggregateId, 1));
        assertEquals(1L, (long) testSubject.lastSequenceNumberFor(aggregateId).orElse(-1L));
        assertFalse(testSubject.lastSequenceNumberFor("inexistent").isPresent());
    }

    @Test
    @DirtiesContext
    void customSchemaConfig() {
        EventSchema testSchema = EventSchema.builder()
                                            .eventTable("CustomDomainEvent")
                                            .payloadColumn("eventData")
                                            .build();
        setTestSubject(testSubject = createEngine(
                engineBuilder -> engineBuilder.schema(testSchema).dataType(String.class),
                new HsqlEventTableFactory() {
                    @Override
                    protected String payloadType() {
                        return "LONGVARCHAR";
                    }
                }
        ));

        storeAndLoadEvents();
    }

    @Test
    @DirtiesContext
    void customSchemaConfigTimestampColumn() {
        setTestSubject(testSubject = createTimestampEngine(new HsqlEventTableFactory() {
            @Override
            protected String timestampType() {
                return "timestamp";
            }
        }));
        storeAndLoadEvents();
    }

    @Test
    void gapsForVeryOldEventsAreNotIncluded() throws SQLException {
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
    void oldGapsAreRemovedFromProvidedTrackingToken() throws SQLException {
        testSubject = createEngine(engineBuilder -> engineBuilder.gapTimeout(50001).gapCleaningThreshold(50));

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
    void eventsWithUnknownPayloadTypeDoNotResultInError() throws SQLException, InterruptedException {
        String expectedPayloadOne = "Payload3";
        String expectedPayloadTwo = "Payload4";

        int testBatchSize = 2;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));
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
    void streamCrossesConsecutiveGapsOfMoreThanBatchSuccessfully() throws SQLException {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));
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
    void streamDoesNotCrossExtendedGapWhenDisabled() throws SQLException {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize)
                                                                 .extendedGapCheckEnabled(false));

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
    void streamCrossesInitialConsecutiveGapsOfMoreThanBatchSuccessfully() throws SQLException {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

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
    void readEventsForAggregateReturnsTheCompleteStream() {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

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
    void readEventsForAggregateWithGapsReturnsTheCompleteStream() {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

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
    void readEventsForAggregateWithEventsExceedingOneBatchReturnsTheCompleteStream() {
        // Set batch size to 5, so that the number of events exceeds at least one batch
        int testBatchSize = 5;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

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
    void readEventsForAggregateWithEventsExceedingOneBatchAndGapsReturnsTheCompleteStream() {
        // Set batch size to 5, so that the number of events exceeds at least one batch
        int testBatchSize = 5;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

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
    protected JdbcEventStorageEngine createEngine(UnaryOperator<JdbcEventStorageEngine.Builder> customization) {
        return createEngine(customization, HsqlEventTableFactory.INSTANCE);
    }

    private JdbcEventStorageEngine createEngine(UnaryOperator<JdbcEventStorageEngine.Builder> customization,
                                                EventTableFactory eventTableFactory) {
        JdbcEventStorageEngine.Builder engineBuilder =
                JdbcEventStorageEngine.builder()
                                      .eventSerializer(TestSerializer.xStreamSerializer())
                                      .persistenceExceptionResolver(defaultPersistenceExceptionResolver)
                                      .snapshotSerializer(TestSerializer.xStreamSerializer())
                                      .batchSize(100)
                                      .connectionProvider(dataSource::getConnection)
                                      .transactionManager(NoTransactionManager.INSTANCE);
        return doCreateTables(
                eventTableFactory,
                new JdbcEventStorageEngine(customization.apply(engineBuilder))
        );
    }

    private JdbcEventStorageEngine createTimestampEngine(EventTableFactory eventTableFactory) {
        JdbcEventStorageEngine.Builder builder =
                JdbcEventStorageEngine.builder()
                                      .eventSerializer(TestSerializer.xStreamSerializer())
                                      .snapshotSerializer(TestSerializer.xStreamSerializer())
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
