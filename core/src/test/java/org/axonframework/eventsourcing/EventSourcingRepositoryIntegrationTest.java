/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.repository.LockManager;
import org.axonframework.repository.OptimisticLockManager;
import org.axonframework.repository.PessimisticLockManager;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.util.ArrayList;
import java.util.Collection;
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

    private static final int CONCURRENT_MODIFIERS = 10;
    private EventSourcingRepository<SimpleAggregateRoot> repository;
    private String aggregateIdentifier;
    private EventBus mockEventBus;
    private EventStore eventStore;
    private List<Throwable> uncaughtExceptions = new CopyOnWriteArrayList<>();
    private List<Thread> startedThreads = new ArrayList<>();

    @Test(timeout = 60000)
    public void testPessimisticLocking() throws Throwable {
        initializeRepository(new PessimisticLockManager());
        long lastSequenceNumber = executeConcurrentModifications(CONCURRENT_MODIFIERS);

        // with pessimistic locking, all modifications are guaranteed successful
        // note: sequence number 20 means there are 21 events. This includes the one from the setup
        assertEquals(2 * CONCURRENT_MODIFIERS, lastSequenceNumber);
        assertEquals(CONCURRENT_MODIFIERS, getSuccessfulModifications());
    }

    @Test(timeout = 60000)
    public void testOptimisticLocking() throws Throwable {
        // unfortunately, we cannot use @Before on the setUp, because of the TemporaryFolder
        initializeRepository(new OptimisticLockManager());
        long lastSequenceNumber = executeConcurrentModifications(CONCURRENT_MODIFIERS);
        assertTrue("Expected at least one successful modification. Got " + getSuccessfulModifications(),
                   getSuccessfulModifications() >= 1);
        int expectedEventCount = getSuccessfulModifications() * 2;
        assertTrue("It seems that no events have been published at all", lastSequenceNumber >= 0);
        // we publish two events at the time
        verify(mockEventBus, times(expectedEventCount / 2)).publish(isA(DomainEventMessage.class), isA(
                DomainEventMessage.class));
    }

    private int getSuccessfulModifications() {
        return CONCURRENT_MODIFIERS - uncaughtExceptions.size();
    }

    private void initializeRepository(LockManager strategy) {
        eventStore = new InMemoryEventStore();
        repository = new EventSourcingRepository<>(new SimpleAggregateFactory(), eventStore,
                                                   strategy);
        mockEventBus = mock(EventBus.class);
        repository.setEventBus(mockEventBus);

        UnitOfWork uow = DefaultUnitOfWork.startAndGet(null);
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

        DomainEventStream committedEvents = eventStore.readEvents(aggregateIdentifier);
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
                                            final String aggregateIdentifier) {
        Thread t = new Thread(() -> {
            try {
                awaitFor.await();
                UnitOfWork uow = DefaultUnitOfWork.startAndGet(null);
                SimpleAggregateRoot aggregate = repository.load(aggregateIdentifier, null);
                aggregate.doOperation();
                aggregate.doOperation();
                uow.commit();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                reportDone.countDown();
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

        private String identifier;

        private SimpleAggregateRoot() {
            identifier = UUID.randomUUID().toString();
            apply(new StubDomainEvent());
        }

        private SimpleAggregateRoot(String identifier) {
            this.identifier = identifier;
        }

        private void doOperation() {
            apply(new StubDomainEvent());
        }

        @Override
        protected void handle(DomainEventMessage event) {
            identifier = event.getAggregateIdentifier();
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        protected Collection<EventSourcedEntity> getChildEntities() {
            return null;
        }
    }

    private static class SimpleAggregateFactory extends AbstractAggregateFactory<SimpleAggregateRoot> {

        @Override
        public SimpleAggregateRoot doCreateAggregate(String aggregateIdentifier,
                                                     DomainEventMessage firstEvent) {
            return new SimpleAggregateRoot(aggregateIdentifier);
        }

        @Override
        public Class<SimpleAggregateRoot> getAggregateType() {
            return SimpleAggregateRoot.class;
        }
    }

    private class InMemoryEventStore implements EventStore {

        private List<DomainEventMessage> domainEvents = new ArrayList<>();

        @Override
        public void appendEvents(List<DomainEventMessage<?>> events) {
            domainEvents.addAll(events);
        }

        @Override
        public synchronized DomainEventStream readEvents(String identifier) {
            List<DomainEventMessage> relevant = new ArrayList<>();
            for (DomainEventMessage event : domainEvents) {
                if (event.getAggregateIdentifier().equals(identifier)) {
                    relevant.add(event);
                }
            }

            return new SimpleDomainEventStream(relevant);
        }

        @Override
        public DomainEventStream readEvents(String identifier, long firstSequenceNumber,
                                            long lastSequenceNumber) {
            throw new UnsupportedOperationException("Not implemented");
        }

    }
}
