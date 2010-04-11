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

package org.axonframework.repository;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.junit.*;
import org.junit.rules.*;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventSourcingRepositoryIntegrationTest implements Thread.UncaughtExceptionHandler {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private EventSourcingRepository<SimpleAggregateRoot> repository;
    private UUID aggregateIdentifier;
    private EventBus mockEventBus;
    private FileSystemEventStore eventStore;
    private List<Throwable> uncaughtExceptions = new Vector<Throwable>();
    private List<Thread> startedThreads = new ArrayList<Thread>();
    private static final int CONCURRENT_MODIFIERS = 10;

    @Test(timeout = 60000)
    public void testPessimisticLocking() throws Throwable {
        initializeRepository(LockingStrategy.PESSIMISTIC);
        long lastSequenceNumber = executeConcurrentModifications(CONCURRENT_MODIFIERS);

        // with pessimistic locking, all modifications are guaranteed successful
        // note: sequence number 20 means there are 21 events. This includes the one from the setup
        assertEquals(2 * CONCURRENT_MODIFIERS, lastSequenceNumber);
        assertEquals(CONCURRENT_MODIFIERS, getSuccessfulModifications());
    }

    @Test(timeout = 60000)
    public void testOptimisticLocking() throws Throwable {
        // unfortunately, we cannot use @Before on the setUp, because of the TemporaryFolder
        initializeRepository(LockingStrategy.OPTIMISTIC);
        long lastSequenceNumber = executeConcurrentModifications(CONCURRENT_MODIFIERS);
        assertTrue("Expected at least one successful modification. Got " + getSuccessfulModifications(),
                   getSuccessfulModifications() >= 1);
        int expectedEventCount = getSuccessfulModifications() * 2;
        assertEquals(expectedEventCount, lastSequenceNumber);
        System.out.println("Successful modifications: " + getSuccessfulModifications());

        verify(mockEventBus, atLeast(2)).publish(isA(DomainEvent.class));
    }

    private int getSuccessfulModifications() {
        return CONCURRENT_MODIFIERS - uncaughtExceptions.size();
    }

    private void initializeRepository(LockingStrategy strategy) {
        repository = new SimpleEventSourcingRepository(strategy);
        eventStore = new FileSystemEventStore(new XStreamEventSerializer());
        eventStore.setBaseDir(new FileSystemResource(folder.getRoot().getPath() + "/"));
        repository.setEventStore(eventStore);
        mockEventBus = mock(EventBus.class);
        repository.setEventBus(mockEventBus);

        SimpleAggregateRoot aggregate = new SimpleAggregateRoot();
        repository.save(aggregate);
        reset(mockEventBus);
        aggregateIdentifier = aggregate.getIdentifier();
    }

    private long executeConcurrentModifications(final int concurrentModifiers) throws Throwable {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch threadsDone = new CountDownLatch(concurrentModifiers);
        for (int t = 0; t < concurrentModifiers; t++) {
            prepareAggregateModifier(startSignal, threadsDone, repository, aggregateIdentifier);
        }
        startSignal.countDown();
        if (!threadsDone.await(30, TimeUnit.SECONDS)) {
            printDiagnosticInformation();
            fail("Thread found to be alive after timeout. It might be hanging");
        }
        for (Throwable e : uncaughtExceptions) {
            if (!(e instanceof ConcurrencyException)) {
                throw e;
            }
        }

        File eventFile = new File(folder.getRoot(), "test/" + aggregateIdentifier.toString() + ".events");
        assertTrue(eventFile.exists());
        DomainEventStream committedEvents = eventStore.readEvents("test", aggregateIdentifier);
        long lastSequenceNumber = -1;
        while (committedEvents.hasNext()) {
            DomainEvent nextEvent = committedEvents.next();
            assertEquals("Events are not stored sequentially. Most likely due to unlocked concurrent access.",
                         new Long(++lastSequenceNumber),
                         nextEvent.getSequenceNumber());
        }
        return lastSequenceNumber;
    }

    private void printDiagnosticInformation() {
        for (Thread t : startedThreads) {
            System.out.print("## Thread [" + t.getName() + "] did not properly shut down during Locking test. ##");
            if (t.getState() != Thread.State.TERMINATED) {
                for (StackTraceElement ste : t.getStackTrace()) {
                    System.out.println(" - " + ste.toString());
                }
            }
            System.out.println();
        }
    }

    private Thread prepareAggregateModifier(final CountDownLatch awaitFor, final CountDownLatch reportDone,
                                            final EventSourcingRepository<SimpleAggregateRoot> repository,
                                            final UUID aggregateIdentifier) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    awaitFor.await();
                    SimpleAggregateRoot aggregate = repository.load(aggregateIdentifier);
                    aggregate.doOperation();
                    aggregate.doOperation();
                    repository.save(aggregate);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    reportDone.countDown();
                }
            }
        });
        t.setUncaughtExceptionHandler(this);
        startedThreads.add(t);
        t.start();
        return t;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        uncaughtExceptions.add(e);
    }

    private static class SimpleAggregateRoot extends AbstractEventSourcedAggregateRoot {

        private SimpleAggregateRoot() {
            apply(new StubDomainEvent());
        }

        private SimpleAggregateRoot(UUID identifier) {
            super(identifier);
        }

        private void doOperation() {
            apply(new StubDomainEvent());
        }

        @Override
        protected void handle(DomainEvent event) {
        }
    }

    private static class SimpleEventSourcingRepository extends EventSourcingRepository<SimpleAggregateRoot> {

        private SimpleEventSourcingRepository(LockingStrategy lockingStrategy) {
            super(lockingStrategy);
        }

        @Override
        public SimpleAggregateRoot instantiateAggregate(UUID aggregateIdentifier, DomainEvent event) {
            return new SimpleAggregateRoot(aggregateIdentifier);
        }

        @Override
        protected String getTypeIdentifier() {
            return "test";
        }
    }
}
