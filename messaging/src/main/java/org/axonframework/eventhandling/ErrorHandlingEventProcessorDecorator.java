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

import java.util.List;

/**
 * Decorator for {@link EventProcessor} that adds error handling logic using an {@link ErrorHandler}.
 */
public class ErrorHandlingEventProcessorDecorator extends EventProcessorDecorator {
    private final ErrorHandler errorHandler;
    private final String processorName;

    public ErrorHandlingEventProcessorDecorator(EventProcessor delegate, ErrorHandler errorHandler, String processorName) {
        super(delegate);
        this.errorHandler = errorHandler;
        this.processorName = processorName;
    }

    /**
     * Wraps the given processing logic with error handling.
     *
     * @param eventMessages The batch of messages being processed
     * @param processingLogic The logic to execute
     */
    protected void withErrorHandling(List<? extends EventMessage<?>> eventMessages, Runnable processingLogic) {
        try {
            processingLogic.run();
        } catch (Exception e) {
            try {
                errorHandler.handleError(new ErrorContext(processorName, e, eventMessages));
            } catch (Exception handlerException) {
                throw new EventProcessingException("Exception occurred while handling error", handlerException);
            }
        }
    }
} 