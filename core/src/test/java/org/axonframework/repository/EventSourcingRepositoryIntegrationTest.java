/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventSourcingRepositoryIntegrationTest implements Thread.UncaughtExceptionHandler {

    private EventSourcingRepository<SimpleAggregateRoot> repository;
    private Object aggregateIdentifier;
    private EventBus mockEventBus;
    private EventStore eventStore;
    private List<Throwable> uncaughtExceptions = new CopyOnWriteArrayList<Throwable>();
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
        assertTrue("It seems that no events have been published at all", lastSequenceNumber >= 0);
        verify(mockEventBus, times(expectedEventCount)).publish(isA(DomainEventMessage.class));
    }

    private int getSuccessfulModifications() {
        return CONCURRENT_MODIFIERS - uncaughtExceptions.size();
    }

    private void initializeRepository(LockingStrategy strategy) {
        repository = new EventSourcingRepository<SimpleAggregateRoot>(new SimpleAggregateFactory(), strategy);
        eventStore = new InMemoryEventStore();
        repository.setEventStore(eventStore);
        mockEventBus = mock(EventBus.class);
        repository.setEventBus(mockEventBus);

        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        SimpleAggregateRoot aggregate = new SimpleAggregateRoot();
        repository.add(aggregate);
        uow.commit();

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

        DomainEventStream committedEvents = eventStore.readEvents("SimpleAggregateRoot", aggregateIdentifier);
        long lastSequenceNumber = -1;
        while (committedEvents.hasNext()) {
            DomainEventMessage nextEvent = committedEvents.next();
            assertEquals("Events are not stored sequentially. Most likely due to unlocked concurrent access.",
                         ++lastSequenceNumber,
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
                                            final Object aggregateIdentifier) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    awaitFor.await();
                    UnitOfWork uow = DefaultUnitOfWork.startAndGet();
                    SimpleAggregateRoot aggregate = repository.load(aggregateIdentifier, null);
                    aggregate.doOperation();
                    aggregate.doOperation();
                    uow.commit();
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

        private UUID identifier;

        private SimpleAggregateRoot() {
            identifier = UUID.randomUUID();
            apply(new StubDomainEvent());
        }

        private SimpleAggregateRoot(UUID identifier) {
            this.identifier = identifier;
        }

        private void doOperation() {
            apply(new StubDomainEvent());
        }

        @Override
        protected void handle(DomainEventMessage event) {
        }

        @Override
        protected void initialize(Object aggregateIdentifier) {
            identifier = (UUID) aggregateIdentifier;
        }

        @Override
        public UUID getIdentifier() {
            return identifier;
        }
    }

    private static class SimpleAggregateFactory implements AggregateFactory<SimpleAggregateRoot> {

        @Override
        public SimpleAggregateRoot createAggregate(Object aggregateIdentifier,
                                                   DomainEventMessage firstEvent) {
            return new SimpleAggregateRoot((UUID) aggregateIdentifier);
        }

        @Override
        public String getTypeIdentifier() {
            return "SimpleAggregateRoot";
        }
    }

    private class InMemoryEventStore implements EventStore {

        private List<DomainEventMessage> domainEvents = new ArrayList<DomainEventMessage>();

        @Override
        public synchronized void appendEvents(String type, DomainEventStream events) {
            while (events.hasNext()) {
                domainEvents.add(events.next());
            }
        }

        @Override
        public synchronized DomainEventStream readEvents(String type, Object identifier) {
            List<DomainEventMessage> relevant = new ArrayList<DomainEventMessage>();
            for (DomainEventMessage event : domainEvents) {
                if (event.getAggregateIdentifier().equals(identifier)) {
                    relevant.add(event);
                }
            }

            return new SimpleDomainEventStream(relevant);
        }
    }
}
