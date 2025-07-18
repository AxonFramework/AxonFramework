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

package org.axonframework.eventhandling.pipeline;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;

/**
 * An {@link EventProcessingPipeline} implementation that processes events using an {@link EventHandlingComponent}. It
 * iterates over the provided events and handles each one using the given component. This should be the last step in the
 * event processing pipeline, as it directly handles the events.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class HandlingEventProcessingPipeline implements EventProcessingPipeline {

    private final EventHandlingComponent eventHandlingComponent;

    /**
     * Creates a new {@link HandlingEventProcessingPipeline} that processes events using the given
     * {@link EventHandlingComponent}.
     *
     * @param eventHandlingComponent The component to handle events.
     */
    public HandlingEventProcessingPipeline(@Nonnull EventHandlingComponent eventHandlingComponent) {
        this.eventHandlingComponent = Objects.requireNonNull(eventHandlingComponent,
                                                             "EventHandlingComponent must not be null");
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events, ProcessingContext context) {
        MessageStream.Empty<Message<Void>> batchResult = MessageStream.empty();
        for (var event : events) {
            var eventResult = eventHandlingComponent.handle(event, context);
            batchResult = batchResult.concatWith(eventResult).ignoreEntries();
        }
        return batchResult;
    }
}
