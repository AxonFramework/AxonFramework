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

package org.axonframework.eventhandling.processors.subscribing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.errorhandling.ErrorHandler;
import org.axonframework.eventhandling.processors.errorhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.tracing.DefaultEventProcessorSpanFactory;
import org.axonframework.eventhandling.tracing.EventProcessorSpanFactory;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.interceptors.DefaultHandlerInterceptorRegistry;
import org.axonframework.messaging.interceptors.HandlerInterceptorRegistry;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Configuration class for a {@link SubscribingEventProcessor}.
 * <p>
 * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
 * {@link EventProcessorSpanFactory} is defaulted to a {@link DefaultEventProcessorSpanFactory} backed by a
 * {@link org.axonframework.tracing.NoOpSpanFactory}, the {@link MessageMonitor} defaults to a
 * {@link NoOpMessageMonitor}, the {@link EventProcessingStrategy} defaults to a {@link DirectEventProcessingStrategy}
 * and the {@link UnitOfWorkFactory} defaults to the
 * {@link org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory}. The Event Processor
 * {@link SubscribableMessageSource} is <b>hard requirements</b> and as such should be provided.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SubscribingEventProcessorConfiguration extends EventProcessorConfiguration {

    private SubscribableMessageSource<? extends EventMessage> messageSource;
    private EventProcessingStrategy processingStrategy = DirectEventProcessingStrategy.INSTANCE;
    private HandlerInterceptorRegistry interceptorRegistry = new DefaultHandlerInterceptorRegistry();

    /**
     * Constructs a new {@code SubscribingEventProcessorConfiguration} with default values.
     */
    public SubscribingEventProcessorConfiguration() {
        super();
    }

    /**
     * Constructs a new {@code SubscribingEventProcessorConfiguration} copying properties from the given configuration.
     *
     * @param base The {@link EventProcessorConfiguration} to copy properties from.
     */
    public SubscribingEventProcessorConfiguration(@Nonnull EventProcessorConfiguration base) {
        super(base);
    }

    @Override
    public SubscribingEventProcessorConfiguration errorHandler(@Nonnull ErrorHandler errorHandler) {
        super.errorHandler(errorHandler);
        return this;
    }

    @Override
    public SubscribingEventProcessorConfiguration messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage> messageMonitor) {
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
     * @param messageSource The {@link SubscribableMessageSource} (e.g. the {@link EventBus}) to which this
     *                      {@link EventProcessor} implementation will subscribe itself to receive
     *                      {@link EventMessage}s.
     * @return The current instance, for fluent interfacing.
     */
    public SubscribingEventProcessorConfiguration messageSource(
            @Nonnull SubscribableMessageSource<? extends EventMessage> messageSource) {
        assertNonNull(messageSource, "SubscribableMessageSource may not be null");
        this.messageSource = messageSource;
        return this;
    }

    /**
     * Sets the {@link EventProcessingStrategy} determining whether events are processed directly or asynchronously.
     * Defaults to a {@link DirectEventProcessingStrategy}.
     *
     * @param processingStrategy The {@link EventProcessingStrategy} determining whether events are processed directly
     *                           or asynchronously.
     * @return The current instance, for fluent interfacing.
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

    /**
     * Registers the given {@link EventMessage}-specific {@link MessageHandlerInterceptor} for the
     * {@link SubscribingEventProcessor} under construction.
     *
     * @param interceptor The {@link EventMessage}-specific {@link MessageHandlerInterceptor} to register for the
     *                    {@link SubscribingEventProcessor} under construction.
     * @return This {@code SubscribingEventProcessorConfiguration}, for fluent interfacing.
     */
    @Nonnull
    public SubscribingEventProcessorConfiguration withInterceptor(
            @Nonnull MessageHandlerInterceptor<EventMessage> interceptor
    ) {
        return withInterceptor(c -> interceptor);
    }

    /**
     * Registers the given {@link EventMessage}-specific {@link MessageHandlerInterceptor} factory for the
     * {@link SubscribingEventProcessor} under construction.
     *
     * @param interceptorBuilder The builder constructing the {@link EventMessage}-specific
     *                           {@link MessageHandlerInterceptor}.
     * @return This {@code SubscribingEventProcessorConfiguration}, for fluent interfacing.
     */
    @Nonnull
    public SubscribingEventProcessorConfiguration withInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<EventMessage>> interceptorBuilder
    ) {
        interceptorRegistry = interceptorRegistry.registerEventInterceptor(interceptorBuilder);
        return this;
    }

    /**
     * Validates whether the fields contained in this Builder are set accordingly.
     *
     * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
     *                                    specifications.
     */
    @Override
    protected void validate() throws AxonConfigurationException {
        super.validate();
        assertNonNull(messageSource, "The SubscribableMessageSource is a hard requirement and should be provided");
    }

    /**
     * Returns the {@link SubscribableMessageSource} to which this processor subscribes.
     *
     * @return The {@link SubscribableMessageSource} for receiving events.
     */
    public SubscribableMessageSource<? extends EventMessage> messageSource() {
        return messageSource;
    }

    /**
     * Returns the {@link EventProcessingStrategy} determining how events are processed.
     *
     * @return The {@link EventProcessingStrategy} for this processor.
     */
    public EventProcessingStrategy processingStrategy() {
        return processingStrategy;
    }

    /**
     * Returns the list of {@link EventMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors} to
     * add to the {@link SubscribingEventProcessor} under construction.
     *
     * @param config The configuration to construct all {@link EventMessage}-specific
     *               {@link MessageHandlerInterceptor MessageHandlerInterceptors}
     * @return The list of {@link EventMessage}-specific {@link MessageHandlerInterceptor MessageHandlerInterceptors} to
     * add to the {@link SubscribingEventProcessor} under construction.
     */
    @Nonnull
    public List<MessageHandlerInterceptor<EventMessage>> interceptors(Configuration config) {
        // First retrieve the default interceptors
        List<MessageHandlerInterceptor<EventMessage>> interceptors =
                config.getComponent(HandlerInterceptorRegistry.class)
                      .eventInterceptors(config);
        // Then add the SEP-specific interceptors
        interceptors.addAll(interceptorRegistry.eventInterceptors(config));
        // And return
        return interceptors;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        super.describeTo(descriptor);
        descriptor.describeProperty("messageSource", messageSource);
        descriptor.describeProperty("processingStrategy", processingStrategy);
        descriptor.describeProperty("interceptors", interceptorRegistry);
    }
}
