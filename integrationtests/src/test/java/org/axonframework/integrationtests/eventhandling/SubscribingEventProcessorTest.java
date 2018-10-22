/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.eventhandling;

import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.integrationtests.utils.EventTestUtils;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.*;

public class SubscribingEventProcessorTest {

    private SubscribingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventMessageHandler mockHandler;

    @Before
    public void setUp() {
        mockHandler = mock(EventMessageHandler.class);
        eventHandlerInvoker = SimpleEventHandlerInvoker.builder().eventHandlers(mockHandler).build();
        eventBus = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        testSubject = SubscribingEventProcessor.builder()
                                               .name("test")
                                               .eventHandlerInvoker(eventHandlerInvoker)
                                               .messageSource(eventBus)
                                               .build();
    }

    @After
    public void tearDown() {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    public void testRestartSubscribingEventProcessor() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();
        testSubject.shutDown();
        testSubject.start();

        eventBus.publish(EventTestUtils.createEvents(2));
        assertTrue("Expected Handler to have received 2 published events", countDownLatch.await(5, TimeUnit.SECONDS));
    }
}
