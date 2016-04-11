/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore;

/**
 * @author Rene de Waele
 */
public class EmbeddedEventStoreTest {


//    @Test
//    @Transactional
//    public void testLoad_LargeAmountOfEventsWithSnapshot() {
//        testSubject.appendEvents(createEvents(110));
//        testSubject.storeSnapshot(createEvent(30));
//        entityManager.flush();
//        entityManager.clear();
//
//        DomainEventStream events = testSubject.readEvents(aggregateIdentifier);
//        long t = 30L;
//        while (events.hasNext()) {
//            DomainEventMessage event = events.next();
//            assertEquals(t, event.getSequenceNumber());
//            t++;
//        }
//        assertEquals(110L, t);
//    }
//
//    @Test
//    @Transactional
//    public void testLoadWithSnapshotEvent() {
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 0, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 1, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 2, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 3, "payload"));
//        entityManager.flush();
//        entityManager.clear();
//        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(type, "id", 3, "snapshot"));
//        entityManager.flush();
//        entityManager.clear();
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 4, "payload"));
//
//        DomainEventStream actualEventStream = testSubject.readEvents("id");
//        List<DomainEventMessage> domainEvents = new ArrayList<>();
//        while (actualEventStream.hasNext()) {
//            DomainEventMessage next = actualEventStream.next();
//            domainEvents.add(next);
//            assertEquals("id", next.getAggregateIdentifier());
//        }
//
//        assertEquals(2, domainEvents.size());
//    }

//    @Test
//    @Transactional
//    public void testEntireStreamIsReadOnUnserializableSnapshot_WithException() {
//        testSubject.appendEvents(createEvents(110));
//
//        entityManager.persist(new SnapshotEventEntry(createEvent(30), StubSerializer.EXCEPTION_ON_SERIALIZATION));
//        entityManager.flush();
//        entityManager.clear();
//
//        assertEquals(0L, testSubject.readEvents(AGGREGATE).findFirst().get().getSequenceNumber());
//    }
//
//    @Test
//    @Transactional
//    public void testEntireStreamIsReadOnUnserializableSnapshot_WithError() {
//        testSubject.appendEvents(createEvents(110));
//
//        entityManager.persist(new SnapshotEventEntry(createEvent(30), StubSerializer.ERROR_ON_SERIALIZATION));
//        entityManager.flush();
//        entityManager.clear();
//
//        assertEquals(0L, testSubject.readEvents(AGGREGATE).findFirst().get().getSequenceNumber());
//    }
//
//    @DirtiesContext
//    @Test
//    @Transactional
//    public void testPrunesSnapshotsWhenNumberOfSnapshotsExceedsConfiguredMaxSnapshotsArchived() {
//        testSubject.setMaxSnapshotsArchived(1);
//
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 0, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 1, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 2, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 3, "payload"));
//        entityManager.flush();
//        entityManager.clear();
//
//        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(type, "id", 3, "snapshot"));
//        entityManager.flush();
//        entityManager.clear();
//
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 4, "payload"));
//        entityManager.flush();
//        entityManager.clear();
//
//        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(type, "id", 4, "snapshot"));
//        entityManager.flush();
//        entityManager.clear();
//
//        @SuppressWarnings({"unchecked"})
//        List<SnapshotEventEntry> snapshots =
//                entityManager.createQuery("SELECT e FROM SnapshotEventEntry e "
//                                                  + "WHERE e.aggregateIdentifier = :aggregateIdentifier")
//                        .setParameter("aggregateIdentifier", "id")
//                        .getResultList();
//        assertEquals("archived snapshot count", 1L, snapshots.size());
//        assertEquals("archived snapshot sequence", 4L, snapshots.iterator().next().getSequenceNumber());
//    }
}
