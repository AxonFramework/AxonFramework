/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DefaultEventProcessorSpanFactory;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.integrationtests.utils.EventTestUtils;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscribingEventProcessorTest {

    private SubscribingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventMessageHandler mockHandler;
    private TestingTransactionManager transactionManager;
    private TestSpanFactory spanFactory;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        mockHandler = mock(EventMessageHandler.class);
        when(mockHandler.canHandle(any())).thenReturn(true);
        eventHandlerInvoker = SimpleEventHandlerInvoker.builder().eventHandlers(mockHandler).build();
        eventBus = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        transactionManager = new TestingTransactionManager();
        testSubject = SubscribingEventProcessor.builder()
                                               .name("test")
                                               .eventHandlerInvoker(eventHandlerInvoker)
                                               .messageSource(eventBus)
                                               .transactionManager(transactionManager)
                                               .spanFactory(DefaultEventProcessorSpanFactory.builder()
                                                                                            .spanFactory(spanFactory)
                                                                                            .build())
                                               .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    void restartSubscribingEventProcessor() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();
        testSubject.shutDown();
        testSubject.start();

        eventBus.publish(EventTestUtils.createEvents(2));
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Handler to have received 2 published events");
    }

    @Test
    void subscribingEventProcessorIsTraced() throws Exception {
        doAnswer(invocation -> {
            EventMessage<?> message = invocation.getArgument(0, EventMessage.class);
            spanFactory.verifySpanActive("EventProcessor.process", message);
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();

        List<DomainEventMessage<?>> events = EventTestUtils.createEvents(2);
        eventBus.publish(events);
        events.forEach(e -> spanFactory.verifySpanCompleted("EventProcessor.process", e));
    }

    @Test
    void startTransactionManager() throws Exception {
        testSubject.start();
        eventBus.publish(EventTestUtils.createEvents(1));

        assertTrue(transactionManager.started, "Expected Transaction to be started");
    }

    @Test
    void buildWithNullTransactionManagerThrowsAxonConfigurationException() {
        SubscribingEventProcessor.Builder builder = SubscribingEventProcessor.builder();

        assertThrows(AxonConfigurationException.class,  () -> builder.transactionManager(null));
    }

    @Test
    void reportIgnoredCalledForEventsWithoutHandler() throws Exception {
        AtomicInteger ignoredCount = new AtomicInteger(0);
        CountDownLatch countDownLatch = new CountDownLatch(2);

        MessageMonitor<EventMessage<?>> messageMonitor = message -> new MessageMonitor.MonitorCallback() {
            @Override
            public void reportSuccess() {
                // Not expected to be called
            }

            @Override
            public void reportFailure(Throwable cause) {
                // Not expected to be called
            }

            @Override
            public void reportIgnored() {
                ignoredCount.incrementAndGet();
                countDownLatch.countDown();
            }
        };

        // Handler that cannot handle any event
        when(mockHandler.canHandle(any())).thenReturn(false);
        when(mockHandler.canHandleType(any())).thenReturn(false);

        testSubject.shutDown();
        testSubject = SubscribingEventProcessor.builder()
                                               .name("test")
                                               .eventHandlerInvoker(eventHandlerInvoker)
                                               .messageSource(eventBus)
                                               .transactionManager(transactionManager)
                                               .messageMonitor(messageMonitor)
                                               .build();
        testSubject.start();

        eventBus.publish(EventTestUtils.createEvents(2));
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS),
                   "Expected MessageMonitor.reportIgnored to have been called for 2 events");
        assertEquals(2, ignoredCount.get(), "Expected ignored count to be 2");
    }

    static class TestingTransactionManager implements TransactionManager {
        private boolean started;

        @Override
        public Transaction startTransaction() {
            started  = true;
            return NoTransactionManager.INSTANCE.startTransaction();
        }
    }

}
