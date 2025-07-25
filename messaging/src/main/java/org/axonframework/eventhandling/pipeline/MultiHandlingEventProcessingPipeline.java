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
import org.axonframework.eventhandling.ProcessorEventHandlingComponents;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TBD
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class MultiHandlingEventProcessingPipeline implements EventProcessingPipeline {

    private final ProcessorEventHandlingComponents eventHandlingComponents;

    /**
     * Constructs a new pipeline that processes events using the given {@link EventHandlingComponent}.
     *
     * @param eventHandlingComponent The component to handle events.
     */
    public MultiHandlingEventProcessingPipeline(@Nonnull ProcessorEventHandlingComponents eventHandlingComponents) {
        this.eventHandlingComponents = Objects.requireNonNull(eventHandlingComponents,
                                                              "ProcessorEventHandlingComponents must not be null");
    }

    // todo: what if two event components have same sequence identifier?
    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events,
                                                      ProcessingContext context) {
        MessageStream.Empty<Message<Void>> result = MessageStream.empty();
        for (var component : eventHandlingComponents.toList()) {
            result = result.concatWith(processComponent(events, context, component)).ignoreEntries();
        }
        return result;
    }

    private static MessageStream.Empty<Message<Void>> processComponent(List<? extends EventMessage<?>> events,
                                                                       ProcessingContext context,
                                                                       EventHandlingComponent component) {
        Map<Object, List<EventMessage<?>>> eventGroups = groupEventsBySequenceIdentifier(events, component, context);

        eventGroups.forEach((sequenceIdentifier, group) -> {
            context.onInvocation(ctx -> {
                MessageStream.Empty<Message<Void>> batchResult = MessageStream.empty();
                for (var event : group) {
                    var eventResult = component.handle(event, context);
                    batchResult = batchResult.concatWith(eventResult).ignoreEntries(); // todo: check if concatWith is enough for serial processing!
                }
                return batchResult.asCompletableFuture();
            });
        });
        return MessageStream.empty();
    }

    private static Map<Object, List<EventMessage<?>>> groupEventsBySequenceIdentifier(
            List<? extends EventMessage<?>> events,
            EventHandlingComponent component,
            ProcessingContext context) {
        Map<Object, List<EventMessage<?>>> eventGroups = new LinkedHashMap<>();

        for (EventMessage<?> event : events) {
            Object sequenceId = component.sequenceIdentifierFor(event, context);
            eventGroups.computeIfAbsent(sequenceId, k -> new ArrayList<>()).add(event);
        }

        return eventGroups;
    }
}
