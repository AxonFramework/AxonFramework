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

package org.axonframework.eventhandling.pooled;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.ProcessorEventHandlingComponents;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.pipeline.BranchingMultiEventProcessingPipeline;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.axonframework.utils.DelegateScheduledExecutorService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link PooledStreamingEventProcessor}.
 *
 * @author Mateusz Nowak
 */
class PooledStreamingEventProcessorTest_MultipleEventHandlingComponent {

    private static final Logger logger = LoggerFactory.getLogger(
            PooledStreamingEventProcessorTest_MultipleEventHandlingComponent.class);

    private static final String PROCESSOR_NAME = "test";

    private PooledStreamingEventProcessor testSubject;
    private AsyncInMemoryStreamableEventSource stubMessageSource;
    private InMemoryTokenStore tokenStore;
    private ScheduledExecutorService coordinatorExecutor;
    private ScheduledExecutorService workerExecutor;

    @BeforeEach
    void setUp() {
        stubMessageSource = new AsyncInMemoryStreamableEventSource();
        tokenStore = spy(new InMemoryTokenStore());
        coordinatorExecutor = new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(2));
        workerExecutor = new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(8));
    }

    private void setTestSubject(PooledStreamingEventProcessor testSubject) {
        this.testSubject = testSubject;
    }

    private PooledStreamingEventProcessor createTestSubject(
            ProcessorEventHandlingComponents eventHandlingComponents,
            UnaryOperator<PooledStreamingEventProcessorsCustomization> configOverride
    ) {
        var customization = configOverride.apply(new PooledStreamingEventProcessorsCustomization());
        return new PooledStreamingEventProcessor(
                PROCESSOR_NAME,
                stubMessageSource,
                new BranchingMultiEventProcessingPipeline(eventHandlingComponents),// new MultiHandlingEventProcessingPipeline(eventHandlingComponents)
                eventHandlingComponents,
                new SimpleUnitOfWorkFactory(),
                tokenStore,
                processorName -> coordinatorExecutor,
                processorName -> workerExecutor,
                customization
        );
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        coordinatorExecutor.shutdown();
        workerExecutor.shutdown();
    }

    @Test
    void handlingSingleEventByMultipleEventHandlingComponents() {
        // given
        var eventHandlingComponent1 = spy(new SimpleEventHandlingComponent());
        eventHandlingComponent1.subscribe(new QualifiedName(String.class), (event, ctx) -> MessageStream.empty());
        var eventHandlingComponent2 = spy(new SimpleEventHandlingComponent());
        eventHandlingComponent2.subscribe(new QualifiedName(String.class), (event, ctx) -> MessageStream.empty());

        var components = new ProcessorEventHandlingComponents(List.of(eventHandlingComponent1, eventHandlingComponent2));
        setTestSubject(
                createTestSubject(components, customization -> customization.initialSegmentCount(1))
        );

        // when
        EventMessage<Integer> supportedEvent = EventTestUtils.asEventMessage("Payload");
        stubMessageSource.publishMessage(supportedEvent);
        testSubject.start();

        // then
        await().atMost(1, TimeUnit.SECONDS)
               .untilAsserted(() -> assertThat(testSubject.processingStatus()).hasSize(1));

        // then
        verify(eventHandlingComponent1, times(1)).handle(eq(supportedEvent), any());
        verify(eventHandlingComponent2, times(1)).handle(eq(supportedEvent), any());

        // then
        await().atMost(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   long currentPosition = testSubject.processingStatus().get(0).getCurrentPosition().orElse(0);
                   assertThat(currentPosition).isEqualTo(1);
               });
    }
}

