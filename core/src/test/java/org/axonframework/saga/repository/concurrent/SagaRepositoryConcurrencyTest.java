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

package org.axonframework.saga.repository.concurrent;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.saga.annotation.AnnotatedSagaManager;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class SagaRepositoryConcurrencyTest implements Thread.UncaughtExceptionHandler {

    private static final int SAGA_COUNT = 20;
    private static final int UPDATE_EVENT_COUNT = 250;

    private VirtualSagaRepository repository;
    private List<Throwable> exceptions = new ArrayList<>();
    private AnnotatedSagaManager sagaManager;

    @Before
    public void setUp() throws Exception {
        repository = new VirtualSagaRepository();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConcurrentAccessToSaga_NotSynchronized() throws Throwable {
        sagaManager = new AnnotatedSagaManager(repository, ConcurrentSaga.class);
        sagaManager.setSynchronizeSagaAccess(false);
        executeConcurrentAccessToSaga(ConcurrentSaga.class);
    }

    @Test
    public void testConcurrentAccessToSaga_Synchronized() throws Throwable {
        sagaManager = new AnnotatedSagaManager(repository, NonConcurrentSaga.class);
        sagaManager.setSynchronizeSagaAccess(true);
        executeConcurrentAccessToSaga(NonConcurrentSaga.class);
    }

    public <T extends AbstractTestSaga> void executeConcurrentAccessToSaga(Class<T> type) throws Throwable {
        final CyclicBarrier startCdl = new CyclicBarrier(SAGA_COUNT);
        final BlockingQueue<EventMessage> eventsToPublish = new ArrayBlockingQueue<>(
                UPDATE_EVENT_COUNT * SAGA_COUNT);
        eventsToPublish.addAll(generateEvents(UPDATE_EVENT_COUNT * SAGA_COUNT));
        final AtomicInteger counter = new AtomicInteger(0);
        List<Thread> threads = prepareThreads(SAGA_COUNT, () -> {
            String id = Integer.toString(counter.getAndIncrement());
            try {
                sagaManager.handle(eventWith(new CreateEvent(id)));
            } catch (Exception e) {
                fail("The saga event handler failed");
            }
            try {
                startCdl.await();
            } catch (InterruptedException e) {
                fail("The thread failed");
            } catch (BrokenBarrierException e) {
                fail("The barrier has been broken");
            }
            boolean mustContinue = true;
            while (mustContinue) {
                EventMessage item = eventsToPublish.poll();
                if (item == null) {
                    mustContinue = false;
                } else {
                    try {
                        sagaManager.handle(item);
                    } catch (Exception e) {
                        fail("The saga event handler failed");
                    }
                }
            }
            try {
                sagaManager.handle(eventWith(new DeleteEvent(id)));
            } catch (Exception e) {
                fail("The saga event handler failed");
            }
        });
        awaitThreadTermination(threads);
        // now, all threads have ended
        List<T> deletedSagas = repository.getDeletedSagas(type);
        Set<T> uniqueInstances = new HashSet<>(deletedSagas);
        assertEquals(SAGA_COUNT, uniqueInstances.size());
        for (T deletedSaga : deletedSagas) {
            List<Object> events = deletedSaga.getEvents();
            assertTrue("Wrong number of events", events.size() <= UPDATE_EVENT_COUNT + 2);
            assertTrue("The first event should always be the creation event. Another event might indicate"
                               + "a lack of thread safety", CreateEvent.class.isInstance(events.get(0)));
                assertTrue("Last should be deletion", DeleteEvent.class.isInstance(events.get(events.size() - 1)));
        }
    }

    private EventMessage eventWith(Object payload) {
        return new GenericEventMessage<>(payload);
    }

    private void awaitThreadTermination(List<Thread> threads) throws Throwable {
        for (Thread thread : threads) {
            thread.join();
        }
        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    private List<EventMessage> generateEvents(int eventCount) {
        List<EventMessage> events = new ArrayList<>(eventCount);
        for (int t = 0; t < eventCount; t++) {
            String sagaId = Integer.toString(t % SAGA_COUNT);
            events.add(eventWith(new UpdateEvent(sagaId)));
        }
        Collections.shuffle(events);
        return events;
    }

    private List<Thread> prepareThreads(int threadCount, Runnable runnable) {
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler(this);
            thread.start();
            threads.add(thread);
        }
        return threads;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        this.exceptions.add(e);
    }
}
