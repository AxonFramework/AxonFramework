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

import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.RecordingEventHandlingComponent;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
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
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link PooledStreamingEventProcessor}.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
class PooledStreamingEventProcessorTest {

    private static final Logger logger = LoggerFactory.getLogger(
            PooledStreamingEventProcessorTest.class);

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

    private void withTestSubject(PooledStreamingEventProcessor testSubject) {
        this.testSubject = testSubject;
    }

    private PooledStreamingEventProcessor processor(
            List<EventHandlingComponent> eventHandlingComponents,
            UnaryOperator<PooledStreamingEventProcessorsCustomization> configOverride
    ) {
        var customization = configOverride.apply(new PooledStreamingEventProcessorsCustomization());
        return new PooledStreamingEventProcessor(
                PROCESSOR_NAME,
                stubMessageSource,
                eventHandlingComponents,
                new SimpleUnitOfWorkFactory(), // todo: this will be BatchUnitOfWorkFactory
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
    void handlingEventsByMultipleEventHandlingComponents() {
        // given
        var eventHandlingComponent1 = new RecordingEventHandlingComponent(new SimpleEventHandlingComponent());
        eventHandlingComponent1.subscribe(new QualifiedName(String.class), (event, ctx) -> MessageStream.empty());
        var eventHandlingComponent2 = new RecordingEventHandlingComponent(new SimpleEventHandlingComponent());
        eventHandlingComponent2.subscribe(new QualifiedName(String.class), (event, ctx) -> MessageStream.empty());

        List<EventHandlingComponent> components = List.of(eventHandlingComponent1, eventHandlingComponent2);
        withTestSubject(
                processor(components, customization -> customization.initialSegmentCount(1))
        );

        // when
        EventMessage<Integer> supportedEvent1 = EventTestUtils.asEventMessage("Payload");
        EventMessage<Integer> supportedEvent2 = EventTestUtils.asEventMessage("Payload");
        stubMessageSource.publishMessage(supportedEvent1);
        stubMessageSource.publishMessage(supportedEvent2);
        testSubject.start();

        // then
        await().atMost(1, TimeUnit.SECONDS)
               .untilAsserted(() -> assertThat(testSubject.processingStatus()).hasSizeGreaterThan(0));

        // then
        assertThat(eventHandlingComponent1.recorded()).containsExactly(supportedEvent1, supportedEvent2);
        assertThat(eventHandlingComponent2.recorded()).containsExactly(supportedEvent1, supportedEvent2);

        // then
        await().atMost(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   long currentPosition = testSubject.processingStatus().get(0).getCurrentPosition().orElse(0);
                   assertThat(currentPosition).isEqualTo(2);
               });
    }
}

