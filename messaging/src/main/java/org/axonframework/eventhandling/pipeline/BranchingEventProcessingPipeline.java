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
public class BranchingEventProcessingPipeline implements EventProcessingPipeline {

    private final EventHandlingComponent eventHandlingComponent;
    private final ExecutorService executorService;

    // todo: configure better the scheduled thread pool!
    public BranchingEventProcessingPipeline(@Nonnull EventHandlingComponent eventHandlingComponent) {
        this(eventHandlingComponent, Executors.newScheduledThreadPool(4, new AxonThreadFactory("BranchingEventProcessingPipeline")));
    }

    public BranchingEventProcessingPipeline(@Nonnull EventHandlingComponent eventHandlingComponent,
                                          @Nonnull ExecutorService executorService) {
        this.eventHandlingComponent = Objects.requireNonNull(eventHandlingComponent,
                                                             "EventHandlingComponent must not be null");
        this.executorService = Objects.requireNonNull(executorService,
                                                     "ExecutorService must not be null");
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events, ProcessingContext context) {
        if (events.isEmpty()) {
            return MessageStream.empty();
        }

        // Group events by sequence identifier
        Map<Object, List<EventMessage<?>>> eventGroups = groupEventsBySequenceIdentifier(events, context);

        // If there's only one group, process it directly without thread pool
        if (eventGroups.size() == 1) {
            return processEventGroup(eventGroups.values().iterator().next(), context);
        }

        // Process multiple groups in parallel
        List<CompletableFuture<MessageStream.Empty<Message<Void>>>> futures = new ArrayList<>();

        for (List<EventMessage<?>> eventGroup : eventGroups.values()) {
            CompletableFuture<MessageStream.Empty<Message<Void>>> future =
                CompletableFuture.supplyAsync(() -> processEventGroup(eventGroup, context), executorService);
            futures.add(future);
        }

        // Wait for all futures to complete in parallel, then gather results
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allFutures.get(); // Wait for all to complete

            // Now gather results from all completed futures
            MessageStream.Empty<Message<Void>> batchResult = MessageStream.empty();
            for (CompletableFuture<MessageStream.Empty<Message<Void>>> future : futures) {
                MessageStream.Empty<Message<Void>> groupResult = future.get(); // This won't block since all are complete
                batchResult = batchResult.concatWith(groupResult).ignoreEntries();
            }

            return batchResult;
        } catch (Exception e) {
            throw new RuntimeException("Error processing event groups", e);
        }
    }

    private Map<Object, List<EventMessage<?>>> groupEventsBySequenceIdentifier(List<? extends EventMessage<?>> events,
                                                                              ProcessingContext context) {
        Map<Object, List<EventMessage<?>>> eventGroups = new LinkedHashMap<>();

        for (EventMessage<?> event : events) {
            Object sequenceId = eventHandlingComponent.sequenceIdentifierFor(event, context);
            eventGroups.computeIfAbsent(sequenceId, k -> new ArrayList<>()).add(event);
        }

        return eventGroups;
    }

    private MessageStream.Empty<Message<Void>> processEventGroup(List<EventMessage<?>> events, ProcessingContext context) {
        MessageStream.Empty<Message<Void>> groupResult = MessageStream.empty();
        for (EventMessage<?> event : events) {
            var eventResult = eventHandlingComponent.handle(event, context);
            groupResult = groupResult.concatWith(eventResult).ignoreEntries();
        }
        return groupResult;
    }
}
