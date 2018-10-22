/*
 * Copyright (c) 2018. AxonIQ
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

package org.axonframework.axonserver.connector.processor;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Controller for {@link EventProcessor}s.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class EventProcessorController {

    private final EventProcessingConfiguration eventProcessingConfiguration;

    private final Deque<Consumer<String>> pauseHandlers = new ArrayDeque<>();

    private final Deque<Consumer<String>> startHandlers = new ArrayDeque<>();

    public EventProcessorController(EventProcessingConfiguration eventProcessingConfiguration) {
        this.eventProcessingConfiguration = eventProcessingConfiguration;
    }

    public EventProcessor getEventProcessor(String processorName){
        return this.eventProcessingConfiguration
                .eventProcessor(processorName)
                .orElseThrow(() -> new RuntimeException("Processor not found"));
    }

    public void pauseProcessor(String processor){
        getEventProcessor(processor).shutDown();
        this.pauseHandlers.forEach(consumer -> consumer.accept(processor));
    }

    public void startProcessor(String processor){
        getEventProcessor(processor).start();
        this.startHandlers.forEach(consumer -> consumer.accept(processor));
    }

    public void releaseSegment(String processor, int segmentId){
        EventProcessor eventProcessor = getEventProcessor(processor);
        if (eventProcessor instanceof TrackingEventProcessor){
            ((TrackingEventProcessor) eventProcessor).releaseSegment(segmentId);
        }
    }

    public void onPause(Consumer<String> consumer){
        this.pauseHandlers.add(consumer);
    }

    public void onStart(Consumer<String> consumer){
        this.startHandlers.add(consumer);
    }
}
