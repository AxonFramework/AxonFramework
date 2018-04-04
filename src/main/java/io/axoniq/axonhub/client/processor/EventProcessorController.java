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

package io.axoniq.axonhub.client.processor;

import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventProcessor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Created by Sara Pellegrini on 09/03/2018.
 * sara.pellegrini@gmail.com
 */
public class EventProcessorController {

    private final EventHandlingConfiguration eventHandlingConfiguration;

    private final Deque<Consumer<String>> pauseHandlers = new ArrayDeque<>();

    private final Deque<Consumer<String>> startHandlers = new ArrayDeque<>();

    public EventProcessorController(EventHandlingConfiguration eventHandlingConfiguration) {
        this.eventHandlingConfiguration = eventHandlingConfiguration;
    }

    private EventProcessor getEventProcessor(String processorName){
        return this.eventHandlingConfiguration
                .getProcessor(processorName)
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

    public void onPause(Consumer<String> consumer){
        this.pauseHandlers.add(consumer);
    }

    public void onStart(Consumer<String> consumer){
        this.startHandlers.add(consumer);
    }
}
