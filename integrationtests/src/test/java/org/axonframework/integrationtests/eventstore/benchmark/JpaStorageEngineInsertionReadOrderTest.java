/*
 * Copyright (c) 2010-2016. Axon Framework
 *
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

package org.axonframework.integrationtests.eventstore.benchmark;

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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

    private static final Logger logger = LoggerFactory.getLogger(JpaStorageEngineInsertionReadOrderTest.class);
    private final Serializer serializer = new XStreamSerializer();

    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    private PlatformTransactionManager tx;

    private BatchingEventStorageEngine testSubject;
    private TransactionTemplate txTemplate;

    @Before
    public void setUp() throws Exception {
        txTemplate = new TransactionTemplate(tx);
        txTemplate.execute(ts -> {
            entityManager.createQuery("DELETE FROM DomainEventEntry").executeUpdate();
            return null;
        });

        testSubject = new JpaEventStorageEngine(serializer, NoOpEventUpcaster.INSTANCE, null, 20,
                                                new SimpleEntityManagerProvider(entityManager),
                                                new SpringTransactionManager(tx),
                                                1L, 10000, true);
    }

    @Test(timeout = 30000)
    public void testInsertConcurrentlyAndCheckReadOrder() throws Exception {
        int threadCount = 10, eventsPerThread = 100, inverseRollbackRate = 7, rollbacksPerThread =
                (eventsPerThread + inverseRollbackRate - 1) / inverseRollbackRate;
        int expectedEventCount = threadCount * eventsPerThread - rollbacksPerThread * threadCount;
        Thread[] writerThreads = storeEvents(threadCount, eventsPerThread, inverseRollbackRate);
        List<TrackedEventMessage<?>> readEvents = readEvents(expectedEventCount);
        for (Thread thread : writerThreads) {
            thread.join();
        }
        assertEquals("The actually read list of events is shorted than the expected value", expectedEventCount,
                     readEvents.size());
    }

    @Test(timeout = 10000)
    public void testInsertConcurrentlyAndReadUsingBlockingStreams() throws Exception {
        int threadCount = 10, eventsPerThread = 100, inverseRollbackRate = 2, rollbacksPerThread =
                (eventsPerThread + inverseRollbackRate - 1) / inverseRollbackRate;
        int expectedEventCount = threadCount * eventsPerThread - rollbacksPerThread * threadCount;
        Thread[] writerThreads = storeEvents(threadCount, eventsPerThread, inverseRollbackRate);
        EmbeddedEventStore embeddedEventStore = new EmbeddedEventStore(testSubject);
        TrackingEventStream readEvents = embeddedEventStore.openStream(null);
        int counter = 0;
        while (counter < expectedEventCount) {
            if (readEvents.hasNextAvailable()) {
                counter++;
            }
        }
        for (Thread thread : writerThreads) {
            thread.join();
        }
        assertEquals("The actually read list of events is shorted than the expected value", expectedEventCount,
                     counter);
    }

    @Test(timeout = 30000)
    public void testInsertConcurrentlyAndReadUsingBlockingStreams_SlowConsumer() throws Exception {
        //increase batch size to 100
        testSubject = new JpaEventStorageEngine(serializer, NoOpEventUpcaster.INSTANCE, null, 100,
                                                new SimpleEntityManagerProvider(entityManager),
                                                new SpringTransactionManager(tx), 1L, 10000, true);
        int threadCount = 4, eventsPerThread = 100, inverseRollbackRate = 2, rollbacksPerThread =
                (eventsPerThread + inverseRollbackRate - 1) / inverseRollbackRate;
        int expectedEventCount = threadCount * eventsPerThread - rollbacksPerThread * threadCount;
        Thread[] writerThreads = storeEvents(threadCount, eventsPerThread, inverseRollbackRate);
        EmbeddedEventStore embeddedEventStore =
                new EmbeddedEventStore(testSubject, null, 20, 1000, 100, TimeUnit.MILLISECONDS);
        TrackingEventStream readEvents = embeddedEventStore.openStream(null);
        int counter = 0;
        while (counter < expectedEventCount) {
            readEvents.nextAvailable();
            counter++;
            if (counter % 50 == 0) {
                Thread.sleep(200);
            }
        }
        for (Thread thread : writerThreads) {
            thread.join();
        }
        assertEquals("The actually read list of events is shorted than the expected value", expectedEventCount,
                     counter);
    }

    private Thread[] storeEvents(int threadCount, int eventsPerThread, int inverseRollbackRate) {
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    final int s = j;
                    try {
                        txTemplate.execute(ts -> {
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

    private List<TrackedEventMessage<?>> readEvents(int eventCount) {
        List<TrackedEventMessage<?>> result = new ArrayList<>();
        TrackingToken lastToken = null;
        while (result.size() < eventCount) {
            List<? extends TrackedEventMessage<?>> batch =
                    testSubject.readEvents(lastToken, false).collect(Collectors.toList());
            for (TrackedEventMessage<?> message : batch) {
                result.add(message);
                if (logger.isDebugEnabled()) {
                    logger.debug(message.getPayload() + " / " + ((DomainEventMessage<?>) message).getSequenceNumber() +
                                         " => " + message.trackingToken().toString());
                }
                lastToken = message.trackingToken();
            }
        }
        return result;
    }

}
