/*
 * Copyright (c) 2011. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.domain.*;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Allard Buijze
 */
public class AsynchronousEventHandlerWrapperTest {

    private AsynchronousEventHandlerWrapper testSubject;
    private StubEventListener mockEventListener;
    private ScheduledThreadPoolExecutor executorService;

    @Before
    public void setUp() {
        mockEventListener = new StubEventListener();
        executorService = new ScheduledThreadPoolExecutor(25);
        executorService.setMaximumPoolSize(100);
        executorService.setKeepAliveTime(1, TimeUnit.MINUTES);
        testSubject = new AsynchronousEventHandlerWrapper(mockEventListener,
                new SequentialPerAggregatePolicy(), executorService);
    }

    @Test
    public void testEventsAreExecutedInOrder() throws InterruptedException {
        AggregateIdentifier[] groupIds = new AggregateIdentifier[100];
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(groupIds.length);
        int eventsPerGroup = 100;
        for (int t = 0; t < groupIds.length; t++) {
            groupIds[t] = startEventDispatcher(start, finish, eventsPerGroup);
        }
        start.countDown();
        finish.await();
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        BlockingQueue<Event> actualEventOrder = mockEventListener.events;
        assertEquals("Expected all events to be dispatched", eventsPerGroup * groupIds.length, actualEventOrder.size());

        for (AggregateIdentifier groupId : groupIds) {
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

    private AggregateIdentifier startEventDispatcher(final CountDownLatch waitToStart, final CountDownLatch waitToEnd,
                                                     int eventCount) {
        AggregateIdentifier id = new UUIDAggregateIdentifier();
        final List<Event> events = new LinkedList<Event>();
        for (int t = 0; t < eventCount; t++) {
            events.add(new StubDomainEvent(id, t));
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waitToStart.await();
                    for (Event event : events) {
                        testSubject.handle(event);
                    }
                } catch (InterruptedException e) {
                    // then we don't dispatch anything
                }
                waitToEnd.countDown();
            }
        }).start();
        return id;
    }

    private static class StubEventListener implements EventListener {

        private final BlockingQueue<Event> events = new ArrayBlockingQueue<Event>(10000);

        @Override
        public void handle(Event event) {
            events.add(event);
        }

    }

}
