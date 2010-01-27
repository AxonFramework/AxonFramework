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

import org.axonframework.core.DomainEvent;
import org.axonframework.core.Event;
import org.axonframework.core.StubDomainEvent;
import org.junit.*;
import org.springframework.util.StopWatch;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class ListenerManagerTest {

    private EventHandlingSequenceManager testSubject;
    private StubEventListener mockEventListener;
    private ScheduledThreadPoolExecutor executorService;

    @Before
    public void setUp() {
        mockEventListener = new StubEventListener();
        executorService = new ScheduledThreadPoolExecutor(25);
        executorService.setMaximumPoolSize(100);
        executorService.setKeepAliveTime(1, TimeUnit.MINUTES);
        testSubject = new EventHandlingSequenceManager(mockEventListener, executorService);
    }

    @Test
    public void testEventsAreExecutedInOrder() throws InterruptedException {
        StopWatch sw = new StopWatch();
        sw.start("Generate events");
        UUID[] groupIds = new UUID[100];
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(groupIds.length);
        int eventsPerGroup = 100;
        for (int t = 0; t < groupIds.length; t++) {
            groupIds[t] = startEventDispatcher(start, finish, eventsPerGroup);
        }
        sw.stop();
        sw.start("Dispatching events");
        start.countDown();
        finish.await();
        sw.stop();
        long taskTime = sw.getTaskInfo()[sw.getTaskCount() - 1].getTimeMillis();
        sw.start("Wait for executor shutdown");
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        sw.stop();
        System.out.println(String.format("Dispatched %s items in %s milliseconds",
                                         (eventsPerGroup * groupIds.length),
                                         taskTime));
        System.out.println(sw.prettyPrint());
        BlockingQueue<Event> actualEventOrder = mockEventListener.events;
        assertEquals("Expected all events to be dispatched", eventsPerGroup * groupIds.length, actualEventOrder.size());

        for (UUID groupId : groupIds) {
            long lastFromGroup = -1;
            for (Event event : actualEventOrder) {
                DomainEvent domainEvent = (DomainEvent) event;
                if (groupId.equals(domainEvent.getAggregateIdentifier())) {
                    assertEquals("Expected all events of same aggregate to be handled sequentially",
                                 ++lastFromGroup,
                                 (long) domainEvent.getSequenceNumber());
                }
            }
        }
    }

    private UUID startEventDispatcher(final CountDownLatch waitToStart, final CountDownLatch waitToEnd,
                                      int eventCount) {
        UUID uuid = UUID.randomUUID();
        final List<Event> events = new LinkedList<Event>();
        for (int t = 0; t < eventCount; t++) {
            events.add(new StubDomainEvent(uuid, t));
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waitToStart.await();
                    for (Event event : events) {
                        testSubject.addEvent(event);
                    }
                } catch (InterruptedException e) {
                    // then we don't dispatch anything
                }
                waitToEnd.countDown();
            }
        }).start();
        return uuid;
    }

    private class StubEventListener implements EventListener {

        private final BlockingQueue<Event> events = new LinkedBlockingQueue<Event>();

        @Override
        public boolean canHandle(Class<? extends Event> eventType) {
            return true;
        }

        @Override
        public void handle(Event event) {
            events.add(event);
        }

        @Override
        public EventSequencingPolicy getEventSequencingPolicy() {
            return new SequentialPerAggregatePolicy();
        }
    }

}
