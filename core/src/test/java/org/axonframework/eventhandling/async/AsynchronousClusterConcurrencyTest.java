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
import org.axonframework.eventhandling.EventListener;
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

    @Test(timeout = 30000)
    public void testHandleEvents() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        testSubject.subscribe(new EventListener() {
            @Override
            public void handle(EventMessage event) {
                counter.incrementAndGet();
            }
        });
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

        int threadCount = 100;
        for (int i = 0; i < threadCount; i++) {
            executor.submit(new EventsPublisher());
        }
        while (counter.get() < threadCount * 30000) {
            Thread.sleep(10);
        }

        executor.shutdown();
        assertTrue("Executor not closed within a reasonable timeframe", executor.awaitTermination(10,
                                                                                                  TimeUnit.SECONDS));

        assertEquals(0, failed.get());
        assertEquals(threadCount * 30000, counter.get());
        assertEquals(threadCount * 30000, completed.get());
    }

    private class EventsPublisher implements Runnable {

        @Override
        public void run() {
            for (int i = 0; i < 10000; i++) {
                testSubject.publish(asEventMessage("1"));
                testSubject.publish(asEventMessage("2"));
                testSubject.publish(asEventMessage("3"));
            }
        }
    }
}
