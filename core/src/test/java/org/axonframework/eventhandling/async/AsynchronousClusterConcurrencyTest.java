/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventhandling.async;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventProcessingMonitor;
import org.junit.*;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AsynchronousClusterConcurrencyTest {

    private ExecutorService executor;
    private AsynchronousCluster testSubject;

    @Before
    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();
        testSubject = new AsynchronousCluster("async", executor, new SequencingPolicy<EventMessage<?>>() {
            @Override
            public Object getSequenceIdentifierFor(EventMessage<?> event) {
                return event.getPayload();
            }
        });
    }

    @Test(timeout = EventsPublisher.EVENTS_COUNT)
    public void testHandleEvents() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        testSubject.subscribe(event -> counter.incrementAndGet());
        testSubject.subscribeEventProcessingMonitor(new EventProcessingMonitor() {
            @Override
            public void onEventProcessingCompleted(List<? extends EventMessage> eventMessages) {
                completed.addAndGet(eventMessages.size());
            }

            @Override
            public void onEventProcessingFailed(List<? extends EventMessage> eventMessages, Throwable cause) {
                failed.incrementAndGet();
            }
        });

        int threadCount = 50;
        for (int i = 0; i < threadCount; i++) {
            executor.submit(new EventsPublisher());
        }
        while (counter.get() < threadCount * EventsPublisher.EVENTS_COUNT) {
            Thread.sleep(10);
        }

        executor.shutdown();
        assertTrue("Executor not closed within a reasonable timeframe", executor.awaitTermination(10,
                                                                                                  TimeUnit.SECONDS));

        assertEquals(0, failed.get());
        assertEquals(threadCount * EventsPublisher.EVENTS_COUNT, counter.get());
        assertEquals(threadCount * EventsPublisher.EVENTS_COUNT, completed.get());
    }

    private class EventsPublisher implements Runnable {

        private static final int ITERATIONS = 10000;
        private static final int EVENTS_COUNT = ITERATIONS * 3;

        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS; i++) {
                testSubject.handle(asEventMessage("1"));
                testSubject.handle(asEventMessage("2"));
                testSubject.handle(asEventMessage("3"));
            }
        }
    }
}
