/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.mockito.Mockito.*;

public class SubscribingEventProcessorTest {

    private SubscribingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventListener mockListener;


    @Before
    public void setUp() throws Exception {
        mockListener = mock(EventListener.class);
        eventHandlerInvoker = new SimpleEventHandlerInvoker(mockListener);
        eventBus = new EmbeddedEventStore(new InMemoryEventStorageEngine());
        testSubject = new SubscribingEventProcessor("test", eventHandlerInvoker, eventBus);
    }

    @After
    public void tearDown() throws Exception {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    public void testRestartSubscribingEventProcessor() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());

        testSubject.start();
        testSubject.shutDown();
        testSubject.start();

        eventBus.publish(createEvents(2));
        assertTrue("Expected listener to have received 2 published events", countDownLatch.await(5, TimeUnit.SECONDS));
    }
}
