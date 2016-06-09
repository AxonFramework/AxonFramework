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

package org.axonframework.integrationtests.eventstore.benchmark;

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GlobalIndexTrackingToken;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.AGGREGATE;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.junit.Assert.assertEquals;

/**
 * @author Rene de Waele
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/insertion-read-order-test-context.xml")
public class JpaStorageEngineInsertionReadOrderTest {

    private final Serializer serializer = new XStreamSerializer();

    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    private PlatformTransactionManager tx;

    private TransactionTemplate txReadCommitted;
    private TransactionTemplate txReadUncommitted;

    private BatchingEventStorageEngine testSubject;

    @Before
    public void setUp() throws Exception {
        txReadCommitted = new TransactionTemplate(tx);
        txReadCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        txReadUncommitted = new TransactionTemplate(tx);
        txReadUncommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);

        txReadCommitted.execute(ts -> {
            entityManager.createQuery("DELETE FROM DomainEventEntry").executeUpdate();
            return null;
        });



        testSubject = new JpaEventStorageEngine(new SimpleEntityManagerProvider(entityManager),
                                                new SpringTransactionManager(tx));

        testSubject.setSerializer(serializer);
        testSubject.setBatchSize(20);
    }

    @Test
    public void testInsertConcurrentlyAndCheckReadOrder() throws Exception {
        System.out.println("Getting ready....");
        int threadCount = 10, eventsPerThread = 100, inverseRollbackRate = 7, rollbacksPerThread =
                (eventsPerThread + inverseRollbackRate - 1) / inverseRollbackRate;
        Thread[] writerThreads = storeEvents(threadCount, eventsPerThread, inverseRollbackRate);
        List<TrackedEventMessage<?>> readEvents = readEvents(threadCount * eventsPerThread);
        for (Thread thread : writerThreads) {
            thread.join();
        }
        int expectedEventCount = threadCount * eventsPerThread - rollbacksPerThread * threadCount;
        assertEquals("The actually read list of events is shorted than the expected value", expectedEventCount,
                     readEvents.size());
    }

    private Thread[] storeEvents(int threadCount, int eventsPerThread, int inverseRollbackRate) {
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    final int s = j;
                    try {
                        txReadCommitted.execute(ts -> {
                            testSubject.appendEvents(
                                    createEvent(AGGREGATE, threadIndex * eventsPerThread + s, "Thread" + threadIndex));
                            if (s % inverseRollbackRate == 0) {
                                throw new RuntimeException("Rolling back on purpose");
                            }
                            try {
                                Thread.sleep(ThreadLocalRandom.current().nextInt(10));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            return null;
                        });
                    } catch (Exception ignored) {
                    }
                }
            });
            threads[i].start();
        }
        return threads;
    }

    private List<TrackedEventMessage<?>> readEvents(int highestTrackingToken) {
        List<TrackedEventMessage<?>> result = new ArrayList<>();
        long lastToken = -1;
        while (lastToken < highestTrackingToken) {
            List<? extends TrackedEventMessage<?>> batch =
                    testSubject.readEvents(new GlobalIndexTrackingToken(lastToken), false).collect(Collectors.toList());
            for (TrackedEventMessage<?> message : batch) {
                if (noGaps(lastToken, ((GlobalIndexTrackingToken) message.trackingToken()).getGlobalIndex())) {
                    result.add(message);
                    System.out.println(message.getPayload() + " / " +
                                               ((DomainEventMessage<?>) message).getSequenceNumber() + " => " +
                                               message.trackingToken().toString());
                    lastToken = ((GlobalIndexTrackingToken) message.trackingToken()).getGlobalIndex();
                } else {
                    System.out.println("Gap detected " + lastToken + " -> " + message.trackingToken() + ". Sleeping");
                    break;
                }
            }
        }
        return result;
    }

    private boolean noGaps(long lastSeq, Long newSeq) {
        if (newSeq == lastSeq + 1) {
            return true;
        }
        List<?> missingIds = txReadUncommitted.execute(ts -> entityManager.createQuery(
                "SELECT p.globalIndex FROM DomainEventEntry p WHERE p.globalIndex > :seqLast " +
                        "AND p.globalIndex < :seqNew", Object.class).setParameter("seqLast", lastSeq)
                .setParameter("seqNew", newSeq).getResultList());
        if (missingIds.isEmpty()) {
            System.out.println("Gap turned out to be a rollback: " + lastSeq + " -> " + newSeq);
        }
        return missingIds.isEmpty();
    }
}
