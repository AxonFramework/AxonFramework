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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.UnknownSerializedType;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.LongStream;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.axonframework.eventsourcing.utils.TestSerializer.xStreamSerializer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link JpaEventStorageEngine}.
 *
 * @author Rene de Waele
 */

class JpaEventStorageEngineTest
        extends BatchingEventStorageEngineTest<JpaEventStorageEngine, JpaEventStorageEngine.Builder> {

    private JpaEventStorageEngine testSubject;

    private final EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("eventStore");
    private final EntityManager entityManager = entityManagerFactory.createEntityManager();
    private final EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
    private final TransactionManager transactionManager = spy(new NoOpTransactionManager());
    private EntityTransaction transaction;
    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;

    @BeforeEach
    void setUp() {
        String databaseProductName = "HSQL Database Engine";
        defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(databaseProductName);
        setTestSubject(testSubject = createEngine());

        transaction = entityManager.getTransaction();
        transaction.begin();
        entityManager.createQuery("DELETE FROM DomainEventEntry dee").executeUpdate();
        transaction.commit();
        entityManager.clear();
        transaction.begin();
    }

    @AfterEach
    public void cleanup(){
        transaction.commit();
    }

    @Test
    void testStoreAndLoadEventsFromDatastore() {
        testSubject.appendEvents(createEvents(2));
        entityManager.clear();
        assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());
    }

    @Test
    void testLoadLastSequenceNumber() {
        testSubject.appendEvents(createEvents(2));
        entityManager.clear();
        assertEquals(1L, (long) testSubject.lastSequenceNumberFor(AGGREGATE).orElse(-1L));
        assertFalse(testSubject.lastSequenceNumberFor(UUID.randomUUID().toString()).isPresent());
    }

    @Test
    void testGapsForVeryOldEventsAreNotIncluded() {
        entityManager.createQuery("DELETE FROM DomainEventEntry dee").executeUpdate();

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

        entityManager.clear();
        entityManager.createQuery("DELETE FROM DomainEventEntry dee WHERE dee.sequenceNumber < 0").executeUpdate();

        testSubject.fetchTrackedEvents(null, 100).stream()
                   .map(i -> (GapAwareTrackingToken) i.trackingToken())
                   .forEach(i -> assertTrue(!i.hasGaps() || i.getGaps().first() >= 5L));
    }

    @DirtiesContext
    @Test
    void testOldGapsAreRemovedFromProvidedTrackingToken() {
        testSubject.setGapCleaningThreshold(50);
        testSubject.setGapTimeout(50001);
        Instant now = Clock.systemUTC().instant();
        GenericEventMessage.clock = Clock.fixed(now.minus(1, ChronoUnit.HOURS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-1), createEvent("aggregateId", 0));
        GenericEventMessage.clock = Clock.fixed(now.minus(2, ChronoUnit.MINUTES), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-2), createEvent("aggregateId", 1));
        GenericEventMessage.clock = Clock.fixed(now.minus(50, ChronoUnit.SECONDS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-3), createEvent("aggregateId", 2));
        GenericEventMessage.clock = Clock.fixed(now, Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-4), createEvent("aggregateId", 3));

        entityManager.clear();
        entityManager.createQuery(
                "DELETE FROM DomainEventEntry dee WHERE dee.aggregateIdentifier <> :aggregateIdentifier")
                     .setParameter("aggregateIdentifier", "aggregateId")
                     .executeUpdate();

        // some "magic" because sequences aren't reset between tests. Finding the sequence positions to use in assertions
        List<Long> sequences = entityManager.createQuery(
                "SELECT e.globalIndex FROM DomainEventEntry e WHERE e.aggregateIdentifier = :aggregateIdentifier",
                Long.class
        )
                                            .setParameter("aggregateIdentifier", "aggregateId").getResultList();
        Optional<Long> maxResult = sequences.stream().max(Long::compareTo);
        assertTrue(maxResult.isPresent());
        long largestIndex = maxResult.get();
        long secondLastEventIndex = largestIndex - 2;
        // create a lot of gaps most of them fake (< 0), but some of them real
        List<Long> gaps = LongStream.range(-50, largestIndex).boxed()
                                    .filter(g -> !sequences.contains(g))
                                    .filter(g -> g < secondLastEventIndex)
                                    .collect(toList());
        List<? extends TrackedEventData<?>> events = testSubject.fetchTrackedEvents(
                GapAwareTrackingToken.newInstance(secondLastEventIndex, gaps), 100
        );
        assertEquals(1, events.size());

        // we expect the gap before the last event we had read previously
        assertEquals(
                secondLastEventIndex - 1,
                (long) ((GapAwareTrackingToken) events.get(0).trackingToken()).getGaps().first()
        );
        // and we've got a new gap in this batch
        assertEquals(2, ((GapAwareTrackingToken) events.get(0).trackingToken()).getGaps().size());
    }

    @Test
    void testUnknownSerializedTypeCausesException() {
        testSubject.appendEvents(createEvent());
        entityManager.createQuery("UPDATE DomainEventEntry e SET e.payloadType = :type").setParameter("type", "unknown")
                     .executeUpdate();
        DomainEventMessage<?> actual = testSubject.readEvents(AGGREGATE).peek();
        assertEquals(UnknownSerializedType.class, actual.getPayloadType());
    }

    @Test
    @SuppressWarnings({"JpaQlInspection", "OptionalGetWithoutIsPresent"})
    @DirtiesContext
    void testStoreEventsWithCustomEntity() {
        XStreamSerializer serializer = xStreamSerializer();
        JpaEventStorageEngine.Builder jpaEventStorageEngineBuilder =
                JpaEventStorageEngine.builder()
                                     .snapshotSerializer(serializer)
                                     .persistenceExceptionResolver(defaultPersistenceExceptionResolver)
                                     .eventSerializer(serializer)
                                     .entityManagerProvider(entityManagerProvider)
                                     .transactionManager(NoTransactionManager.INSTANCE)
                                     .explicitFlush(false);
        testSubject = new JpaEventStorageEngine(jpaEventStorageEngineBuilder) {

            @Override
            protected EventData<?> createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
                return new CustomDomainEventEntry((DomainEventMessage<?>) eventMessage, serializer);
            }

            @Override
            protected DomainEventData<?> createSnapshotEntity(DomainEventMessage<?> snapshot, Serializer serializer) {
                return new CustomSnapshotEventEntry(snapshot, serializer);
            }

            @Override
            protected String domainEventEntryEntityName() {
                return CustomDomainEventEntry.class.getSimpleName();
            }

            @Override
            protected String snapshotEventEntryEntityName() {
                return CustomSnapshotEventEntry.class.getSimpleName();
            }
        };

        testSubject.appendEvents(createEvent(AGGREGATE, 1, "Payload1"));
        testSubject.storeSnapshot(createEvent(AGGREGATE, 1, "Snapshot1"));

        entityManager.flush();
        entityManager.clear();

        assertFalse(entityManager.createQuery("SELECT e FROM CustomDomainEventEntry e").getResultList().isEmpty());
        assertEquals("Snapshot1", testSubject.readSnapshot(AGGREGATE).get().getPayload());
        assertEquals("Payload1", testSubject.readEvents(AGGREGATE).peek().getPayload());
    }

    @Test
    void testEventsWithUnknownPayloadDoNotResultInError() throws InterruptedException {
        String expectedPayloadOne = "Payload3";
        String expectedPayloadTwo = "Payload4";

        int testBatchSize = 2;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));
        EmbeddedEventStore testEventStore = EmbeddedEventStore.builder().storageEngine(testSubject).build();

        testSubject.appendEvents(createEvent(AGGREGATE, 1, "Payload1"), createEvent(AGGREGATE, 2, "Payload2"));
        // Update events which will be part of the first batch to an unknown payload type
        entityManager.createQuery("UPDATE DomainEventEntry e SET e.payloadType = :type").setParameter("type", "unknown")
                     .executeUpdate();
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
        assertFalse(eventStoreResult.hasNextAvailable());
    }

    @Test
    void testAppendEventsIsPerformedInATransaction() {
        testSubject.appendEvents(createEvents(2));

        verify(transactionManager).executeInTransaction(any());
    }

    @Override
    protected JpaEventStorageEngine createEngine(UnaryOperator<JpaEventStorageEngine.Builder> customization) {
        JpaEventStorageEngine.Builder engineBuilder =
                JpaEventStorageEngine.builder()
                                     .upcasterChain(NoOpEventUpcaster.INSTANCE)
                                     .persistenceExceptionResolver(defaultPersistenceExceptionResolver)
                                     .batchSize(100)
                                     .entityManagerProvider(entityManagerProvider)
                                     .transactionManager(transactionManager)
                                     .eventSerializer(xStreamSerializer())
                                     .snapshotSerializer(xStreamSerializer());
        return new JpaEventStorageEngine(customization.apply(engineBuilder));
    }

    /**
     * A non-final {@link TransactionManager} implementation, so that it can be spied upon through Mockito.
     */
    private static class NoOpTransactionManager implements TransactionManager {

        @Override
        public Transaction startTransaction() {
            return new Transaction() {
                @Override
                public void commit() {
                    // No-op
                }

                @Override
                public void rollback() {
                    // No-op
                }
            };
        }
    }
}
