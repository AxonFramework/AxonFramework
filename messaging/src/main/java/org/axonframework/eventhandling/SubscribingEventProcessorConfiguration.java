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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Builder class to instantiate a {@link SubscribingEventProcessor}.
 * <p>
 * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
 * {@link EventProcessorSpanFactory} is defaulted to a {@link DefaultEventProcessorSpanFactory} backed by a
 * {@link org.axonframework.tracing.NoOpSpanFactory}, the {@link MessageMonitor} defaults to a
 * {@link NoOpMessageMonitor}, the {@link EventProcessingStrategy} defaults to a {@link DirectEventProcessingStrategy}
 * and the {@link TransactionManager} defaults to the {@link NoTransactionManager#INSTANCE}. The Event Processor
 * {@code name}, {@link EventHandlerInvoker} and {@link SubscribableMessageSource} are <b>hard requirements</b> and as
 * such should be provided.
 */
public class SubscribingEventProcessorConfiguration extends EventProcessorConfiguration {

    private SubscribableMessageSource<? extends EventMessage<?>> messageSource;
    private EventProcessingStrategy processingStrategy = DirectEventProcessingStrategy.INSTANCE;

    public SubscribingEventProcessorConfiguration() {
        super();
    }

    public SubscribingEventProcessorConfiguration(EventProcessorConfiguration base) {
        super(base);
    }

    @Override
    public SubscribingEventProcessorConfiguration eventHandlerInvoker(
            @Nonnull EventHandlerInvoker eventHandlerInvoker) {
        super.eventHandlerInvoker(eventHandlerInvoker);
        return this;
    }

    @Override
    public SubscribingEventProcessorConfiguration eventHandlingComponents(
            @Nonnull List<EventHandlingComponent> eventHandlingComponents) {
        super.eventHandlingComponents(eventHandlingComponents);
        return this;
    }

    @Override
    public SubscribingEventProcessorConfiguration errorHandler(@Nonnull ErrorHandler errorHandler) {
        super.errorHandler(errorHandler);
        return this;
    }

    @Override
    public SubscribingEventProcessorConfiguration messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super.messageMonitor(messageMonitor);
        return this;
    }

    @Override
    public SubscribingEventProcessorConfiguration spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
        super.spanFactory(spanFactory);
        return this;
    }

    /**
     * Sets the {@link SubscribableMessageSource} (e.g. the {@link EventBus}) to which this {@link EventProcessor}
     * implementation will subscribe itself to receive {@link EventMessage}s.
     *
     * @param messageSource the {@link SubscribableMessageSource} (e.g. the {@link EventBus}) to which this
     *                      {@link EventProcessor} implementation will subscribe itself to receive
     *                      {@link EventMessage}s
     * @return the current Builder instance, for fluent interfacing
     */
    public SubscribingEventProcessorConfiguration messageSource(
            @Nonnull SubscribableMessageSource<? extends EventMessage<?>> messageSource) {
        assertNonNull(messageSource, "SubscribableMessageSource may not be null");
        this.messageSource = messageSource;
        return this;
    }

    /**
     * Sets the {@link EventProcessingStrategy} determining whether events are processed directly or asynchronously.
     * Defaults to a {@link DirectEventProcessingStrategy}.
     *
     * @param processingStrategy the {@link EventProcessingStrategy} determining whether events are processed directly
     *                           or asynchronously
     * @return the current Builder instance, for fluent interfacing
     */
    public SubscribingEventProcessorConfiguration processingStrategy(
            @Nonnull EventProcessingStrategy processingStrategy) {
        assertNonNull(processingStrategy, "EventProcessingStrategy may not be null");
        this.processingStrategy = processingStrategy;
        return this;
    }

    @Override
    public SubscribingEventProcessorConfiguration unitOfWorkFactory(@Nonnull UnitOfWorkFactory unitOfWorkFactory) {
        super.unitOfWorkFactory(unitOfWorkFactory);
        return this;
    }

    @Override
    public SubscribingEventProcessorConfiguration interceptors(
            @Nonnull List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors) {
        super.interceptors(interceptors);
        return this;
    }

    /**
     * Validates whether the fields contained in this Builder are set accordingly.
     *
     * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
     *                                    specifications
     */
    @Override
    protected void validate() throws AxonConfigurationException {
        super.validate();
        assertNonNull(messageSource, "The SubscribableMessageSource is a hard requirement and should be provided");
    }

    public SubscribableMessageSource<? extends EventMessage<?>> messageSource() {
        return messageSource;
    }

    public EventProcessingStrategy processingStrategy() {
        return processingStrategy;
    }
}
