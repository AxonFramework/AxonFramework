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

package org.axonframework.core.eventhandler;

import org.axonframework.core.Event;
import org.axonframework.core.StubDomainEvent;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class EventHandlingSequenceManagerTest {

    private EventListener eventListener;
    private ScheduledThreadPoolExecutor executorService;
    private EventHandlingSequenceManager testSubject;

    private CountDownLatch countdownLatch;
    private Field transactionsField;

    @SuppressWarnings({"unchecked"})
    @Before
    public void setUp() {
        eventListener = new StubEventListener();
        executorService = new ScheduledThreadPoolExecutor(2);
        executorService.setMaximumPoolSize(2);
        executorService.setKeepAliveTime(1, TimeUnit.SECONDS);
        testSubject = new EventHandlingSequenceManager(eventListener, executorService);
    }

    @After
    public void After() throws InterruptedException {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testSequenceManager_SchedulersDiscardedAfterShutdown()
            throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        countdownLatch = new CountDownLatch(1000);
        for (int t = 0; t < 1000; t++) {
            testSubject.addEvent(new StubDomainEvent());
        }
        assertTrue("Processing took too long.", countdownLatch.await(10, TimeUnit.SECONDS));

        executorService.shutdown();
        assertTrue("Shutdown took too long.", executorService.awaitTermination(5, TimeUnit.SECONDS));

        transactionsField = testSubject.getClass().getDeclaredField("transactions");
        transactionsField.setAccessible(true);
        Map transactions = (Map) transactionsField.get(testSubject);
        assertTrue("Expected transaction schedulers to be cleaned up", transactions.isEmpty());
    }

    /**
     * Very useless implementation of EventSequencingPolicy that is the fastest way to display a memory leak
     */
    private class FullRandomPolicy implements EventSequencingPolicy {

        @Override
        public Object getSequenceIdentifierFor(Event event) {
            return event.getEventIdentifier();
        }
    }

    private class StubEventListener implements EventListener {

        @Override
        public boolean canHandle(Class<? extends Event> eventType) {
            return true;
        }

        @Override
        public void handle(Event event) {
            countdownLatch.countDown();
        }

        @Override
        public EventSequencingPolicy getEventSequencingPolicy() {
            return new FullRandomPolicy();
        }
    }
}
