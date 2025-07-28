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
import org.axonframework.eventhandling.ErrorContext;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessingException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * An {@link EventProcessingPipeline} that handles errors by delegating to an {@link ErrorHandler}.
 * <p>
 * If an error occurs during the processing of events, it will call the provided {@link ErrorHandler} with an
 * {@link ErrorContext} containing the name of the event processor, the exception, and the events being processed.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class ErrorHandlingBatchProcessor implements EventProcessingPipeline {

    private final EventProcessingPipeline next;
    private final BiFunction<List<? extends EventMessage<?>>, Throwable, ErrorContext> batchErrorContextFactory;
    private final ErrorHandler errorHandler;

    /**
     * Constructs the pipeline with the given {@code next} pipeline, {@code eventProcessor} name, and
     * {@code errorHandler}.
     *
     * @param next           The next {@link EventProcessingPipeline} to delegate to.
     * @param eventProcessor The name of the event processor.
     * @param errorHandler   The {@link ErrorHandler} to handle errors.
     */
    public ErrorHandlingBatchProcessor(
            @Nonnull ErrorHandler errorHandler,
            @Nonnull BiFunction<List<? extends EventMessage<?>>, Throwable, ErrorContext> batchErrorContextFactory,
            @Nonnull EventProcessingPipeline next
    ) {
        this.next = Objects.requireNonNull(next, "Next may not be null");
        this.batchErrorContextFactory = Objects.requireNonNull(batchErrorContextFactory, "BatchErrorContextFactory may not be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "ErrorHandler may not be null");
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(
            List<? extends EventMessage<?>> events,
            ProcessingContext context
    ) {
        return next.process(events, context)
                   .onErrorContinue(ex -> {
                       try {
                           errorHandler.handleError(batchErrorContextFactory.apply(events, ex));
                       } catch (RuntimeException re) {
                           return MessageStream.failed(re);
                       } catch (Exception e) {
                           return MessageStream.failed(new EventProcessingException(
                                   "Exception occurred while processing events",
                                   e));
                       }
                       return MessageStream.empty().cast();
                   })
                   .ignoreEntries()
                   .cast();
    }
}
