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
 * Base decorator for {@link EventProcessingPipeline} implementations.
 * Forwards all calls to the delegate by default.
 * Subclasses can override methods to add cross-cutting behavior.
 */
abstract class EventProcessingPipelineDecorator implements EventProcessingPipeline {
    protected final EventProcessingPipeline delegate;

    protected EventProcessingPipelineDecorator(EventProcessingPipeline delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Void> processInUnitOfWork(
            List<? extends EventMessage<?>> eventMessages,
            UnitOfWork unitOfWork,
            Collection<Segment> processingSegments
    ) throws Exception {
        return delegate.processInUnitOfWork(eventMessages, unitOfWork, processingSegments);
    }

    @Override
    public String getProcessorName() {
        return delegate.getProcessorName();
    }
} 