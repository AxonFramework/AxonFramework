/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.axonframework.eventhandling.EventTestUtils.createEvents;
import static org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class SubscribingEventProcessorTest {

    private LegacyEmbeddedEventStore eventBus;
    private TestingTransactionManager transactionManager;
    private TestSpanFactory spanFactory;

    private SubscribingEventProcessor testSubject;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        eventBus = LegacyEmbeddedEventStore.builder().storageEngine(new LegacyInMemoryEventStorageEngine()).build();
        transactionManager = new TestingTransactionManager();
        testSubject = withTestSubject(List.of(), config -> config);
    }

    private SubscribingEventProcessor withTestSubject(
            List<EventHandlingComponent> eventHandlingComponents,
            UnaryOperator<SubscribingEventProcessorConfiguration> customization
    ) {
        var configuration = new SubscribingEventProcessorConfiguration()
                .messageSource(eventBus)
                .unitOfWorkFactory(transactionalUnitOfWorkFactory(transactionManager));
        var customized = customization.apply(configuration);
        var processor = new SubscribingEventProcessor(
                "test",
                eventHandlingComponents,
                customized
        );
        this.testSubject = processor;
        return processor;
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    void restartSubscribingEventProcessor() throws Exception {
        // given
        CountDownLatch countDownLatch = new CountDownLatch(2);
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(Integer.class), (event, context) -> {
            countDownLatch.countDown();
            return MessageStream.empty();
        });
        withTestSubject(List.of(eventHandlingComponent), config -> config);

        // when
        testSubject.start();
        testSubject.shutDown();
        testSubject.start();

        // then
        List<EventMessage<Integer>> events = createEvents(2);
        eventBus.publish(events);
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Handler to have received 2 published events");
    }

    @Disabled("TODO #3098 - Support tracking on the level of batch / Unit of Work")
    @Test
    void subscribingEventProcessorIsTraced() {
        // given
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(Integer.class), (event, context) -> {
            spanFactory.verifySpanActive("EventProcessor.process", event);
            return MessageStream.empty();
        });
        withTestSubject(List.of(eventHandlingComponent), config -> config);

        // when
        testSubject.start();

        // then
        List<EventMessage<Integer>> events = createEvents(2);
        eventBus.publish(events);
        events.forEach(e -> spanFactory.verifySpanCompleted("EventProcessor.process", e));
    }

    @Test
    void startTransactionManager() throws Exception {
        testSubject.start();
        eventBus.publish(createEvents(1));

        assertTrue(transactionManager.started, "Expected Transaction to be started");
    }

    @Test
    void buildWithNullUnitOfWorkFactoryThrowsAxonConfigurationException() {
        SubscribingEventProcessorConfiguration builder = new SubscribingEventProcessorConfiguration();

        assertThrows(AxonConfigurationException.class, () -> builder.unitOfWorkFactory(null));
    }

    static class TestingTransactionManager implements TransactionManager {

        private boolean started;

        @Override
        public Transaction startTransaction() {
            started = true;
            return NoTransactionManager.INSTANCE.startTransaction();
        }
    }
}
