/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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

package org.axonframework.config;

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link EventProcessorRegistry}.
 *
 * @author Milan Savic
 * @since 3.3
 */
class DefaultSagaEventProcessorRegistry implements EventProcessorRegistry {

    private final Map<String, EventProcessor> eventProcessors = new HashMap<>();

    @Override
    public EventProcessor registerProcessor(String name, EventProcessor eventProcessor) {
        return eventProcessors.compute(name, (n, old) -> {
            if (old != null) {
                MultiEventHandlerInvoker multiEventHandlerInvoker =
                        new MultiEventHandlerInvoker(eventProcessor.eventHandlerInvoker(), old.eventHandlerInvoker());
                old.setEventHandlerInvoker(multiEventHandlerInvoker);
                return old;
            }
            return eventProcessor;
        });
    }


}
