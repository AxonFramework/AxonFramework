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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.UnknownSerializedType;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;

import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.junit.Assert.*;

/**
 * @author Rene de Waele
 */
@RunWith(SpringJUnit4ClassRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(locations = "classpath:/META-INF/spring/db-context.xml")
@Transactional
public class JpaEventStorageEngineTest extends BatchingEventStorageEngineTest {

    private JpaEventStorageEngine testSubject;

    @PersistenceContext
    private EntityManager entityManager;

    private EntityManagerProvider entityManagerProvider;

    @Autowired
    private DataSource dataSource;

    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;

    @Before
    public void setUp() throws SQLException {
        entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
        defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
        setTestSubject(
                testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver));

        entityManager.createQuery("DELETE FROM DomainEventEntry dee").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    public void testStoreAndLoadEventsFromDatastore() {
        testSubject.appendEvents(createEvents(2));
        entityManager.clear();
        assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());
    }

    @Test
    public void testLoadLastSequenceNumber() {
        testSubject.appendEvents(createEvents(2));
        entityManager.clear();
        assertEquals(1L, (long) testSubject.lastSequenceNumberFor(AGGREGATE).orElse(-1L));
        assertFalse(testSubject.lastSequenceNumberFor(UUID.randomUUID().toString()).isPresent());
    }

    @Test
    public void testGapsForVeryOldEventsAreNotIncluded() {
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
    public void testOldGapsAreRemovedFromProvidedTrackingToken() {
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
        Long largestIndex = sequences.stream().max(Long::compareTo).get();
        Long secondLastEventIndex = largestIndex - 2;
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
    public void testStoreTwoExactSameSnapshots() {
        testSubject.storeSnapshot(createEvent(1));
        entityManager.clear();
        testSubject.storeSnapshot(createEvent(1));
    }

    @Test
    public void testUnknownSerializedTypeCausesException() {
        testSubject.appendEvents(createEvent());
        entityManager.createQuery("UPDATE DomainEventEntry e SET e.payloadType = :type").setParameter("type", "unknown")
                     .executeUpdate();
        DomainEventMessage<?> actual = testSubject.readEvents(AGGREGATE).peek();
        assertEquals(UnknownSerializedType.class, actual.getPayloadType());
    }

    @Test
    @SuppressWarnings({"JpaQlInspection", "OptionalGetWithoutIsPresent"})
    @DirtiesContext
    public void testStoreEventsWithCustomEntity() {
        XStreamSerializer serializer = XStreamSerializer.builder().build();
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
    public void testEventsWithUnknownPayloadDoNotResultInError() throws InterruptedException {
        String expectedPayloadOne = "Payload3";
        String expectedPayloadTwo = "Payload4";
        List<String> expected = Arrays.asList(expectedPayloadOne, expectedPayloadTwo);

        int testBatchSize = 2;
        testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver, testBatchSize);
        EmbeddedEventStore testEventStore = EmbeddedEventStore.builder().storageEngine(testSubject).build();

        testSubject.appendEvents(createEvent(AGGREGATE, 1, "Payload1"),
                                 createEvent(AGGREGATE, 2, "Payload2"));
        // Update events which will be part of the first batch to an unknown payload type
        entityManager.createQuery("UPDATE DomainEventEntry e SET e.payloadType = :type").setParameter("type", "unknown")
                     .executeUpdate();
        testSubject.appendEvents(createEvent(AGGREGATE, 3, expectedPayloadOne),
                                 createEvent(AGGREGATE, 4, expectedPayloadTwo));

        List<String> eventStorageEngineResult = testSubject.readEvents(null, false)
                                                           .filter(m -> m.getPayload() instanceof String)
                                                           .map(m -> (String) m.getPayload())
                                                           .collect(toList());
        assertEquals(expected, eventStorageEngineResult);

        TrackingEventStream eventStoreResult = testEventStore.openStream(null);

        assertTrue(eventStoreResult.hasNextAvailable());
        assertEquals(UnknownSerializedType.class, eventStoreResult.nextAvailable().getPayloadType());
        assertEquals(UnknownSerializedType.class, eventStoreResult.nextAvailable().getPayloadType());
        assertEquals(expectedPayloadOne, eventStoreResult.nextAvailable().getPayload());
        assertEquals(expectedPayloadTwo, eventStoreResult.nextAvailable().getPayload());
        assertFalse(eventStoreResult.hasNextAvailable());
    }

    @Override
    protected AbstractEventStorageEngine createEngine(EventUpcaster upcasterChain) {
        return createEngine(upcasterChain, defaultPersistenceExceptionResolver);
    }

    @Override
    protected AbstractEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        return createEngine(NoOpEventUpcaster.INSTANCE, persistenceExceptionResolver);
    }

    protected JpaEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                 PersistenceExceptionResolver persistenceExceptionResolver) {
        return createEngine(upcasterChain, persistenceExceptionResolver, 100);
    }

    protected JpaEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 int batchSize) {
        return JpaEventStorageEngine.builder()
                                    .upcasterChain(upcasterChain)
                                    .persistenceExceptionResolver(persistenceExceptionResolver)
                                    .batchSize(batchSize)
                                    .entityManagerProvider(entityManagerProvider)
                                    .transactionManager(NoTransactionManager.INSTANCE)
                                    .build();
    }
}
