/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.processor;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Controller used to delegate operations to the {@link EventProcessor}s contained in an application.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class EventProcessorController {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessorController.class);

    private final EventProcessingConfiguration eventProcessingConfiguration;
    private final Deque<Consumer<String>> pauseHandlers = new ArrayDeque<>();
    private final Deque<Consumer<String>> startHandlers = new ArrayDeque<>();

    /**
     * Instantiate a controller to perform operations on the {@link EventProcessor} instances contained in this
     * application. This controller should provide functionality to trigger all the APIs available on the EventProcessor
     * interface and it's implementations.
     *
     * @param eventProcessingConfiguration the {@link EventProcessingConfiguration} from which the existing
     *                                     {@link EventProcessor}s will be retrieved
     */
    public EventProcessorController(EventProcessingConfiguration eventProcessingConfiguration) {
        this.eventProcessingConfiguration = eventProcessingConfiguration;
    }

    EventProcessor getEventProcessor(String processorName) {
        return eventProcessingConfiguration.eventProcessor(processorName)
                                           .orElseThrow(() -> new RuntimeException(
                                                   "Processor [" + processorName + "] not found"
                                           ));
    }

    void pauseProcessor(String processorName) {
        getEventProcessor(processorName).shutDown();
        this.pauseHandlers.forEach(consumer -> consumer.accept(processorName));
    }

    void startProcessor(String processorName) {
        getEventProcessor(processorName).start();
        this.startHandlers.forEach(consumer -> consumer.accept(processorName));
    }

    void releaseSegment(String processorName, int segmentId) {
        EventProcessor eventProcessor = getEventProcessor(processorName);
        if (!(eventProcessor instanceof TrackingEventProcessor)) {
            logger.info("Release segment requested for processor [{}] which is not a Tracking Event Processor");
            return;
        }
        ((TrackingEventProcessor) eventProcessor).releaseSegment(segmentId);
    }

    boolean splitSegment(String processorName, int segmentId) {
        EventProcessor eventProcessor = getEventProcessor(processorName);
        if (!(eventProcessor instanceof TrackingEventProcessor)) {
            logger.info("Split segment requested for processor [{}] which is not a Tracking Event Processor");
            return false;
        }

        return ((TrackingEventProcessor) eventProcessor)
                .splitSegment(segmentId)
                .thenApply(result -> {
                    if (result) {
                        logger.info("Successfully split segment [{}] of processor [{}]",
                                    segmentId, processorName);
                    } else {
                        logger.warn("Was not able to split segment [{}] for processor [{}]",
                                    segmentId, processorName);
                    }
                    return result;
                }).join();
    }

    boolean mergeSegment(String processorName, int segmentId) {
        EventProcessor eventProcessor = getEventProcessor(processorName);
        if (!(eventProcessor instanceof TrackingEventProcessor)) {
            logger.warn("Merge segment request received for processor [{}] which is not a Tracking Event Processor", processorName);
            return false;
        }

        return ((TrackingEventProcessor) eventProcessor)
                .mergeSegment(segmentId)
                .thenApply(result -> {
                    if (result) {
                        logger.info("Successfully merged segment [{}] of processor [{}]",
                                    segmentId, processorName);
                    } else {
                        logger.warn("Was not able to merge segment [{}] for processor [{}]",
                                    segmentId, processorName);
                    }
                    return result;
                }).join();
    }

    /**
     * Add a {@link Consumer} to be called when an {@link EventProcessor} is paused through the
     * {@link EventProcessor#shutDown()} method.
     *
     * @param pauseHandler the {@link Consumer} to be called when an {@link EventProcessor} is paused
     */
    public void onPause(Consumer<String> pauseHandler) {
        this.pauseHandlers.add(pauseHandler);
    }

    /**
     * Add a {@link Consumer} to be called when an {@link EventProcessor} is started through the
     * {@link EventProcessor#start()} ()} method.
     *
     * @param startHandler the {@link Consumer} to be called when an {@link EventProcessor} is started
     */
    public void onStart(Consumer<String> startHandler) {
        this.startHandlers.add(startHandler);
    }
}
