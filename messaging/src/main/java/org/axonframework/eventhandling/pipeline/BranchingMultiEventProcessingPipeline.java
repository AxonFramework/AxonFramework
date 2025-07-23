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
import org.axonframework.common.AxonThreadFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TBD
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class BranchingMultiEventProcessingPipeline implements EventProcessingPipeline {

    private final List<EventHandlingComponent> eventHandlingComponents;
    private final ExecutorService executorService;

    public BranchingMultiEventProcessingPipeline(@Nonnull ProcessorEventHandlingComponents components) {
        this(components.toList());
    }

    // todo: configure better the scheduled thread pool!
    public BranchingMultiEventProcessingPipeline(@Nonnull List<EventHandlingComponent> eventHandlingComponents) {
        this(eventHandlingComponents,
             Executors.newScheduledThreadPool(4, new AxonThreadFactory("BranchingMultiEventProcessingPipeline")));
    }

    public BranchingMultiEventProcessingPipeline(@Nonnull List<EventHandlingComponent> eventHandlingComponents,
                                                 @Nonnull ExecutorService executorService) {
        this.eventHandlingComponents = Objects.requireNonNull(eventHandlingComponents,
                                                              "EventHandlingComponents list must not be null");
        if (eventHandlingComponents.isEmpty()) {
            throw new IllegalArgumentException("EventHandlingComponents list must not be empty");
        }
        this.executorService = Objects.requireNonNull(executorService,
                                                      "ExecutorService must not be null");
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events,
                                                      ProcessingContext context) {
        if (events.isEmpty()) {
            return MessageStream.empty();
        }

        // If there's only one component, use simplified processing without extra parallelization
        if (eventHandlingComponents.size() == 1) {
            return processSingleComponent(eventHandlingComponents.getFirst(), events, context);
        }

        // Process each component in parallel
        List<CompletableFuture<MessageStream.Empty<Message<Void>>>> componentFutures = new ArrayList<>();

        for (EventHandlingComponent component : eventHandlingComponents) {
            CompletableFuture<MessageStream.Empty<Message<Void>>> componentFuture =
                    CompletableFuture.supplyAsync(() -> processSingleComponent(component, events, context),
                                                  executorService);
            // new BranchingEventProcessingPipeline(component).process(events, context)
            componentFutures.add(componentFuture);
        }

        // Wait for all component futures to complete in parallel, then gather results
        CompletableFuture<Void> allComponentFutures = CompletableFuture.allOf(componentFutures.toArray(new CompletableFuture[0]));

        try {
            allComponentFutures.get(); // Wait for all to complete

            // Now gather results from all completed component futures
            MessageStream.Empty<Message<Void>> batchResult = MessageStream.empty();
            for (CompletableFuture<MessageStream.Empty<Message<Void>>> future : componentFutures) {
                MessageStream.Empty<Message<Void>> componentResult = future.get(); // This won't block since all are complete
                batchResult = batchResult.concatWith(componentResult).ignoreEntries();
            }

            return batchResult;
        } catch (Exception e) {
            throw new RuntimeException("Error processing components", e);
        }
    }

    private MessageStream.Empty<Message<Void>> processSingleComponent(EventHandlingComponent component,
                                                                      List<? extends EventMessage<?>> events,
                                                                      ProcessingContext context) {
        // Group events by sequence identifier for this component
        Map<Object, List<EventMessage<?>>> eventGroups = groupEventsBySequenceIdentifier(events, component, context);

        // If there's only one group, process it directly without thread pool
        if (eventGroups.size() == 1) {
            return processEventGroup(component, eventGroups.values().iterator().next(), context);
        }

        // Process multiple groups in parallel for this component
        List<CompletableFuture<MessageStream.Empty<Message<Void>>>> groupFutures = new ArrayList<>();

        for (List<EventMessage<?>> eventGroup : eventGroups.values()) {
            CompletableFuture<MessageStream.Empty<Message<Void>>> groupFuture =
                    CompletableFuture.supplyAsync(() -> processEventGroup(component, eventGroup, context),
                                                  executorService);
            groupFutures.add(groupFuture);
        }

        // Wait for all group futures to complete in parallel, then gather results
        CompletableFuture<Void> allGroupFutures = CompletableFuture.allOf(groupFutures.toArray(new CompletableFuture[0]));

        try {
            allGroupFutures.get(); // Wait for all to complete

            // Now gather results from all completed group futures
            MessageStream.Empty<Message<Void>> componentResult = MessageStream.empty();
            for (CompletableFuture<MessageStream.Empty<Message<Void>>> future : groupFutures) {
                MessageStream.Empty<Message<Void>> groupResult = future.get(); // This won't block since all are complete
                componentResult = componentResult.concatWith(groupResult).ignoreEntries();
            }

            return componentResult;
        } catch (Exception e) {
            throw new RuntimeException("Error processing event groups for component", e);
        }
    }

    private Map<Object, List<EventMessage<?>>> groupEventsBySequenceIdentifier(List<? extends EventMessage<?>> events,
                                                                               EventHandlingComponent component,
                                                                               ProcessingContext context) {
        Map<Object, List<EventMessage<?>>> eventGroups = new LinkedHashMap<>();

        for (EventMessage<?> event : events) {
            Object sequenceId = component.sequenceIdentifierFor(event, context);
            eventGroups.computeIfAbsent(sequenceId, k -> new ArrayList<>()).add(event);
        }

        return eventGroups;
    }

    private MessageStream.Empty<Message<Void>> processEventGroup(EventHandlingComponent component,
                                                                 List<EventMessage<?>> events,
                                                                 ProcessingContext context) {
        MessageStream.Empty<Message<Void>> groupResult = MessageStream.empty();
        for (EventMessage<?> event : events) {
            var eventResult = component.handle(event, context);
            groupResult = groupResult.concatWith(eventResult).ignoreEntries();
        }
        return groupResult;
    }
}
