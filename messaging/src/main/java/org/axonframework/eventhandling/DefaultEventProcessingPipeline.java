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

package org.axonframework.eventhandling;

import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of {@link EventProcessingPipeline} that delegates to the EventHandlingComponent.
 */
public class DefaultEventProcessingPipeline implements EventProcessingPipeline {
    private final String processorName;
    private final EventHandlingComponent eventHandlingComponent;

    public DefaultEventProcessingPipeline(String processorName, EventHandlingComponent eventHandlingComponent) {
        this.processorName = processorName;
        this.eventHandlingComponent = eventHandlingComponent;
    }

    @Override
    public String getProcessorName() {
        return processorName;
    }

    @Override
    public CompletableFuture<Void> processInUnitOfWork(
            List<? extends EventMessage<?>> eventMessages,
            UnitOfWork unitOfWork,
            Collection<Segment> processingSegments
    ) {
        try {
            for (EventMessage<?> event : eventMessages) {
                eventHandlingComponent.handle(event, (org.axonframework.messaging.unitofwork.ProcessingContext) unitOfWork);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
} 