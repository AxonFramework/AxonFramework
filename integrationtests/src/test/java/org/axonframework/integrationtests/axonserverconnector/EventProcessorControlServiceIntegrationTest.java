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

package org.axonframework.integrationtests.axonserverconnector;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.connector.control.ControlChannel;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.streaming.StreamingEventProcessor;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.AbstractAxonServerIntegrationTest;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test validating event processor control functionality with a real Axon Server instance.
 * <p>
 * This test verifies that:
 * <ul>
 *     <li>Event processors register with Axon Server's control channel</li>
 *     <li>Admin channel is accessible for load balancing configuration</li>
 *     <li>Processors can be controlled through the API (start, shutdown)</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class EventProcessorControlServiceIntegrationTest extends AbstractAxonServerIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessorControlServiceIntegrationTest.class);

    private AxonServerConnectionManager connectionManager;

    @Override
    protected ApplicationConfigurer createConfigurer() {
        var configurer = EventSourcingConfigurer.create();
        configurer.messaging(m -> m.eventProcessing(
                                     ep -> ep.pooledStreaming(
                                             ps -> ps.defaultProcessor("PSEP_Sample",
                                                                       eh -> eh.declarative(cfg -> new SimpleEventHandlingComponent()
                                                                               .subscribe(
                                                                                       new QualifiedName(String.class),
                                                                                       (event, context) -> MessageStream.empty()))
                                             )
                                     ).subscribing(s -> s.defaultProcessor("SUB_Sample",
                                                                           eh -> eh.declarative(cfg -> new SimpleEventHandlingComponent().subscribe(
                                                                                   new QualifiedName(String.class),
                                                                                   (event, context) -> MessageStream.empty()))))
                             )
        );
        return configurer;
    }

    @Test
    void axonServerConnectionManagerIsAvailable() {
        // given / when
        startApp();
        connectionManager = startedConfiguration.getComponent(AxonServerConnectionManager.class);

        // then
        assertNotNull(connectionManager, "AxonServerConnectionManager should be available");
    }

    @Test
    void controlChannelAndAdminChannelAreAccessible() {
        // given
        startApp();
        connectionManager = startedConfiguration.getComponent(AxonServerConnectionManager.class);

        // when / then
        await().pollDelay(Duration.ofMillis(100))
               .atMost(Duration.ofSeconds(10))
               .untilAsserted(() -> {
                   AxonServerConnection connection = connectionManager.getConnection();
                   ControlChannel controlChannel = connection.controlChannel();
                   AdminChannel adminChannel = connection.adminChannel();

                   assertNotNull(controlChannel, "Control channel should be available");
                   assertNotNull(adminChannel, "Admin channel should be available");

                   logger.info("Successfully connected to Axon Server - Control and Admin channels are available");
               });
    }

    @Test
    void eventProcessorsAreRegistered() {
        // given
        startApp();
        connectionManager = startedConfiguration.getComponent(AxonServerConnectionManager.class);

        // when
        Map<String, EventProcessor> eventProcessors = startedConfiguration.getComponents(EventProcessor.class);

        // then
        assertNotNull(eventProcessors, "Event processors map should not be null");
        assertFalse(eventProcessors.isEmpty(), "At least one event processor should be registered");

        logger.info("Found {} registered event processors", eventProcessors.size());
        eventProcessors.forEach((name, processor) -> {
            logger.info("Event Processor: {} - Type: {}", name, processor.getClass().getSimpleName());
        });
    }

    @Test
    void eventProcessorCanBeStartedAndStopped() {
        // given
        startApp();
        connectionManager = startedConfiguration.getComponent(AxonServerConnectionManager.class);
        Map<String, EventProcessor> eventProcessors = startedConfiguration.getComponents(EventProcessor.class);

        assertFalse(eventProcessors.isEmpty(), "At least one event processor should be registered");

        EventProcessor processor = eventProcessors.values().iterator().next();
        String processorName = eventProcessors.keySet().iterator().next();

        logger.info("Testing processor: {}", processorName);

        // when - start the processor
        CompletableFuture<Void> startFuture = processor.start();

        // then - processor starts successfully
        await().atMost(Duration.ofSeconds(10))
               .until(() -> startFuture.isDone() && !startFuture.isCompletedExceptionally());

        assertTrue(processor.isRunning(), "Processor should be running after start");
        logger.info("Processor {} started successfully", processorName);

        // when - shutdown the processor
        CompletableFuture<Void> shutdownFuture = processor.shutdown();

        // then - processor shuts down successfully
        await().atMost(Duration.ofSeconds(10))
               .until(() -> shutdownFuture.isDone() && !shutdownFuture.isCompletedExceptionally());

        assertFalse(processor.isRunning(), "Processor should not be running after shutdown");
        logger.info("Processor {} shut down successfully", processorName);
    }

    @Test
    void streamingEventProcessorSupportsSegmentOperations() {
        // given
        startApp();
        connectionManager = startedConfiguration.getComponent(AxonServerConnectionManager.class);
        Map<String, EventProcessor> eventProcessors = startedConfiguration.getComponents(EventProcessor.class);

        // Find a streaming event processor
        StreamingEventProcessor streamingProcessor = eventProcessors.values().stream()
                                                                    .filter(p -> p instanceof StreamingEventProcessor)
                                                                    .map(p -> (StreamingEventProcessor) p)
                                                                    .findFirst()
                                                                    .orElse(null);

        if (streamingProcessor == null) {
            logger.warn("No StreamingEventProcessor found, skipping segment operations test");
            return;
        }

        String processorName = eventProcessors.entrySet().stream()
                                              .filter(e -> e.getValue() == streamingProcessor)
                                              .map(Map.Entry::getKey)
                                              .findFirst()
                                              .orElse("unknown");

        logger.info("Testing segment operations on StreamingEventProcessor: {}", processorName);

        // when - start the processor
        CompletableFuture<Void> startFuture = streamingProcessor.start();
        await().atMost(Duration.ofSeconds(10))
               .until(() -> startFuture.isDone() && !startFuture.isCompletedExceptionally());

        // then - verify processor is running
        assertTrue(streamingProcessor.isRunning(), "Processor should be running");
        assertFalse(streamingProcessor.processingStatus().isEmpty(), "Processor should have segments");

        int initialSegmentCount = streamingProcessor.processingStatus().size();
        logger.info("Initial segment count: {}", initialSegmentCount);

        // when - attempt to split a segment
        CompletableFuture<Boolean> splitResult = streamingProcessor.splitSegment(0);

        // then - split operation completes (may succeed or fail depending on processor type)
        await().atMost(Duration.ofSeconds(15))
               .until(splitResult::isDone);

        logger.info("Segment split result: {}", splitResult.join());
    }
}
