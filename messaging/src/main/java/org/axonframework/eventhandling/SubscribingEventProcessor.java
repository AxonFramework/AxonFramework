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

import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Event processor implementation that {@link EventBus#subscribe(Consumer) subscribes} to the {@link EventBus} for
 * events. Events published on the event bus are supplied to this processor in the publishing thread.
 * <p>
 * Depending on the given {@link EventProcessingStrategy} the events are processed directly (in the publishing thread)
 * or asynchronously.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class SubscribingEventProcessor implements EventProcessor {

    private final SubscribableMessageSource<? extends EventMessage<?>> messageSource;
    private final EventProcessingStrategy processingStrategy;
    private final TransactionalUnitOfWorkFactory transactionalUnitOfWorkFactory;
    private final EventProcessingPipeline pipeline;

    private volatile Registration eventBusRegistration;

    public SubscribingEventProcessor(
            SubscribableMessageSource<? extends EventMessage<?>> messageSource,
            EventProcessingStrategy processingStrategy,
            TransactionManager transactionManager,
            EventProcessingPipeline pipeline
    ) {
        this.messageSource = messageSource;
        this.processingStrategy = processingStrategy;
        this.transactionalUnitOfWorkFactory = new TransactionalUnitOfWorkFactory(transactionManager);
        this.pipeline = pipeline;
    }

    @Override
    public String getName() {
        return pipeline.getProcessorName();
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        // Interceptors are managed by the pipeline; expose if needed
        if (pipeline instanceof InterceptorEventProcessingPipelineDecorator decorator) {
            return decorator.getInterceptors();
        }
        return List.of();
    }

    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super EventMessage<?>> handlerInterceptor) {
        throw new UnsupportedOperationException("Dynamic interceptor registration is not supported in this refactored version. Pass interceptors at construction time.");
    }

    @Override
    public void start() {
        if (eventBusRegistration != null) {
            // This event processor has already been started
            return;
        }
        eventBusRegistration =
                messageSource.subscribe(eventMessages -> processingStrategy.handle(eventMessages, this::process));
    }

    @Override
    public boolean isRunning() {
        return eventBusRegistration != null;
    }

    @Override
    public boolean isError() {
        // this implementation will never stop because of an error
        return false;
    }

    protected void process(List<? extends EventMessage<?>> eventMessages) {
        var unitOfWork = transactionalUnitOfWorkFactory.create();
        try {
            pipeline.processInUnitOfWork(eventMessages, unitOfWork, List.of()).join();
        } catch (Exception e) {
            throw new EventProcessingException("Exception occurred while processing events", e);
        }
    }

    @Override
    public void shutDown() {
        if (eventBusRegistration != null) {
            eventBusRegistration.cancel();
        }
        eventBusRegistration = null;
    }

    public SubscribableMessageSource<? extends EventMessage<?>> getMessageSource() {
        return messageSource;
    }
}
