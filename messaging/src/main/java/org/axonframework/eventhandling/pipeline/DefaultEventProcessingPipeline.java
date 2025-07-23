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
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.ProcessorEventHandlingComponents;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;

@Internal
public class DefaultEventProcessingPipeline implements EventProcessingPipeline {

    private final EventProcessingPipeline delegate;

    public DefaultEventProcessingPipeline(
            @Nonnull String processorName,
            @Nonnull ErrorHandler errorHandler,
            @Nonnull EventProcessorSpanFactory spanFactory,
            @Nonnull ProcessorEventHandlingComponents eventHandlingComponents,
            boolean streaming
    ) {
        this.delegate = new ErrorHandlingEventProcessingPipeline(
                processorName,
                errorHandler,
                new TracingEventProcessingPipeline(
                        (eventsList) -> spanFactory.createBatchSpan(streaming, eventsList),
                        new MultiHandlingEventProcessingPipeline(eventHandlingComponents)
                )
        );
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events,
                                                      ProcessingContext context) {
        return delegate.process(events, context);
    }
}
