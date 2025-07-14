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
 * Decorator for {@link EventProcessingPipeline} that adds error handling logic using an {@link ErrorHandler}.
 */
class ErrorHandlingEventProcessingPipelineDecorator extends EventProcessingPipelineDecorator {
    private final ErrorHandler errorHandler;

    public ErrorHandlingEventProcessingPipelineDecorator(EventProcessingPipeline delegate, ErrorHandler errorHandler) {
        super(delegate);
        this.errorHandler = errorHandler;
    }

    @Override
    public CompletableFuture<Void> processInUnitOfWork(
            List<? extends EventMessage<?>> eventMessages,
            UnitOfWork unitOfWork,
            Collection<Segment> processingSegments
    ) throws Exception {
        try {
            return delegate.processInUnitOfWork(eventMessages, unitOfWork, processingSegments);
        } catch (Exception e) {
            try {
                errorHandler.handleError(new ErrorContext(getProcessorName(), e, eventMessages));
            } catch (Exception handlerException) {
                throw new EventProcessingException("Exception occurred while handling error", handlerException);
            }
            throw e;
        }
    }
} 