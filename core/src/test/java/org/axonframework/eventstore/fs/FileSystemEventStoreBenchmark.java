/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.eventstore.fs;

import com.mongodb.Mongo;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.axonframework.eventstore.jpa.JpaEventStore;
import org.axonframework.eventstore.mongo.AxonMongoWrapper;
import org.axonframework.eventstore.mongo.MongoEventStore;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 * @author Jettro Coenradie
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:/META-INF/spring/benchmark-context.xml"})
//@Ignore("Remove this before running the benchmark test. Some ides discover this class and run it as a normal test")
public class FileSystemEventStoreBenchmark {

    private static final int THREAD_COUNT = 100;
    private static final int TRANSACTION_COUNT = 500;
    private static final int TRANSACTION_SIZE = 2;

    private static FileSystemEventStore fileSystemEventStore;

    @Autowired
    private JpaEventStore jpaEventStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MongoEventStore mongoEventStore;

    @Autowired
    private Mongo mongoDb;

    @BeforeClass
    public static void prepareEventStore() {
        fileSystemEventStore = new FileSystemEventStore(new XStreamEventSerializer());
        fileSystemEventStore.setBaseDir(new FileSystemResource("/data/"));
    }

    // JPA (connection pool 50-150): 100 threads concurrently wrote 100 * 10 events each in 410937.0 milliseconds. That is an average of 243 events per second
    // JPA (connection pool 50-50): 100 threads concurrently wrote 100 * 10 events each in 397969 milliseconds. That is an average of 251 events per second
    // JPA (connection pool 50-150): [Test stopped after writing 55741 events in 1020000 milliseconds]. That is an average of 55 events per second
    // JPA (connection pool 50-50): 100 threads concurrently wrote 500 * 2 events each in 1651781 milliseconds. That is an average of 60 events per second

    @Test
    public void startBenchmarkTest_JPA() throws InterruptedException {
        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<Thread>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            TransactionalBenchmark runnable = new TransactionalBenchmark();
            Thread thread = new Thread(runnable);
            thread.run();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join(60000);
        }
        long end = System.currentTimeMillis();
        System.out.println(String.format(
                "JPA: %s threads concurrently wrote %s * %s events each in %s milliseconds. That is an average of %s events per second",
                THREAD_COUNT,
                TRANSACTION_COUNT,
                TRANSACTION_SIZE,
                (end - start),
                (THREAD_COUNT * TRANSACTION_COUNT * TRANSACTION_SIZE) / ((end - start) / 1000)));
    }

    // FileSystem: 100 threads concurrently wrote 100 * 10 events each in 29625 milliseconds. That is an average of 3448 events per second
    // FileSystem: 100 threads concurrently wrote 500 * 2 events each in 44813 milliseconds. That is an average of 2272 events per second
    // FileSystem (OutputStream pooling): 100 threads concurrently wrote 100 * 10 events each in 19844 milliseconds. That is an average of 5263 events per second
    // FileSystem (OutputStream pooling): 100 threads concurrently wrote 500 * 2 events each in 20047 milliseconds. That is an average of 5000 events per second

    @Test
    public void startBenchmarkTest_FileSystem() throws InterruptedException {
        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<Thread>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            Thread thread = new Thread(new Benchmark());
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long end = System.currentTimeMillis();
        System.out.println(String.format(
                "Filesystem: %s threads concurrently wrote %s * %s events each in %s milliseconds. That is an average of %s events per second",
                THREAD_COUNT,
                TRANSACTION_COUNT,
                TRANSACTION_SIZE,
                (end - start),
                (THREAD_COUNT * TRANSACTION_COUNT * TRANSACTION_SIZE) / ((end - start) / 1000)));
    }

    @Test
    public void startBenchmarkTest_Mongo() throws InterruptedException {
        AxonMongoWrapper axonMongoWrapper = new AxonMongoWrapper(mongoDb);
        axonMongoWrapper.database().dropDatabase();
        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<Thread>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            Thread thread = new Thread(new MongoBenchmark());
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long end = System.currentTimeMillis();
        System.out.println(String.format(
                "Mongo: %s threads concurrently wrote %s * %s events each in %s milliseconds. That is an average of %s events per second",
                THREAD_COUNT,
                TRANSACTION_COUNT,
                TRANSACTION_SIZE,
                (end - start),
                (THREAD_COUNT * TRANSACTION_COUNT * TRANSACTION_SIZE) / ((end - start) / 1000)));
    }

    private int saveAndLoadLargeNumberOfEvents(AggregateIdentifier aggregateId, EventStore eventStore,
                                               int eventSequence) {
        List<DomainEvent> events = new ArrayList<DomainEvent>();
        for (int t = 0; t < TRANSACTION_SIZE; t++) {
            events.add(new StubDomainEvent(aggregateId, eventSequence++));
        }
        eventStore.appendEvents("benchmark", new SimpleDomainEventStream(events));
        return eventSequence;
    }

    private class Benchmark implements Runnable {

        @Override
        public void run() {
            final AggregateIdentifier aggregateId = AggregateIdentifierFactory.randomIdentifier();
            int eventSequence = 0;
            for (int t = 0; t < TRANSACTION_COUNT; t++) {
                eventSequence = saveAndLoadLargeNumberOfEvents(aggregateId, fileSystemEventStore, eventSequence) + 1;
            }
        }
    }

    private class MongoBenchmark implements Runnable {

        @Override
        public void run() {
            final AggregateIdentifier aggregateId = AggregateIdentifierFactory.randomIdentifier();
            final AtomicInteger eventSequence = new AtomicInteger(0);
            for (int t = 0; t < TRANSACTION_COUNT; t++) {
                eventSequence.set(saveAndLoadLargeNumberOfEvents(aggregateId,
                                                                 mongoEventStore,
                                                                 eventSequence.get()) + 1);
            }
        }
    }

    private class TransactionalBenchmark implements Runnable {

        @Override
        public void run() {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            final AggregateIdentifier aggregateId = AggregateIdentifierFactory.randomIdentifier();
            final AtomicInteger eventSequence = new AtomicInteger(0);
            for (int t = 0; t < TRANSACTION_COUNT; t++) {
                template.execute(new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        assertFalse(status.isRollbackOnly());
                        eventSequence.set(saveAndLoadLargeNumberOfEvents(aggregateId,
                                                                         jpaEventStore,
                                                                         eventSequence.get()) + 1);
                    }
                });
            }
        }
    }
}
