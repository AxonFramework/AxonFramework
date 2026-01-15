/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.subscribing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorHandler;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.eventhandling.tracing.DefaultEventProcessorSpanFactory;
import org.axonframework.messaging.eventhandling.tracing.EventProcessorSpanFactory;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.SubscribableEventSource;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.tracing.NoOpSpanFactory;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Configuration class for a {@link SubscribingEventProcessor}.
 * <p>
 * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
 * {@link EventProcessorSpanFactory} is defaulted to a {@link DefaultEventProcessorSpanFactory} backed by a
 * {@link NoOpSpanFactory}, the {@link MessageMonitor} defaults to a
 * {@link NoOpMessageMonitor}, and the {@link UnitOfWorkFactory} defaults to the
 * {@link SimpleUnitOfWorkFactory}. The Event Processor
 * {@link SubscribableEventSource} is <b>hard requirements</b> and as such should be provided.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SubscribingEventProcessorConfiguration extends EventProcessorConfiguration {

    private SubscribableEventSource eventSource;

    /**
     * Constructs a new {@code SubscribingEventProcessorConfiguration}.
     * <p>
     * This configuration will not have any of the default {@link MessageHandlerInterceptor MessageHandlerInterceptors}
     * for events. Please use
     * {@link #SubscribingEventProcessorConfiguration(EventProcessorConfiguration, Configuration)} when those are
     * desired.
     */
    @Internal
    public SubscribingEventProcessorConfiguration() {
        super();
    }

    /**
     * Constructs a new {@code SubscribingEventProcessorConfiguration} copying properties from the given configuration.
     *
     * @param base The {@link EventProcessorConfiguration} to copy properties from.
     */
    @Internal
    public SubscribingEventProcessorConfiguration(@Nonnull EventProcessorConfiguration base) {
        super(base);
    }

    /**
     * Constructs a new {@code SubscribingEventProcessorConfiguration} with default values and retrieve global default
     * values.
     *
     * @param configuration The configuration, used to retrieve global default values, like
     *                      {@link MessageHandlerInterceptor MessageHandlerInterceptors}, from.
     */
    @Internal
    public SubscribingEventProcessorConfiguration(@Nonnull EventProcessorConfiguration base,
                                                  @Nonnull Configuration configuration) {
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
     * Sets the {@link SubscribableEventSource} (e.g. the {@link EventBus}) to which this {@link EventProcessor}
     * implementation will subscribe itself to receive {@link EventMessage}s.
     *
     * @param eventSource The {@link SubscribableEventSource} (e.g. the {@link EventBus}) to which this
     *                      {@link EventProcessor} implementation will subscribe itself to receive
     *                      {@link EventMessage}s.
     * @return The current instance, for fluent interfacing.
     */
    public SubscribingEventProcessorConfiguration eventSource(
            @Nonnull SubscribableEventSource eventSource) {
        assertNonNull(eventSource, "SubscribableEventSource may not be null");
        this.eventSource = eventSource;
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
            @Nonnull MessageHandlerInterceptor<? super EventMessage> interceptor
    ) {
        //noinspection unchecked | Casting to EventMessage is safe.
        this.interceptors.add((MessageHandlerInterceptor<EventMessage>) interceptor);
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
        assertNonNull(eventSource, "The SubscribableMessageSource is a hard requirement and should be provided");
    }

    /**
     * Returns the {@link SubscribableEventSource} to which this processor subscribes.
     *
     * @return The {@link SubscribableEventSource} for receiving events.
     */
    public SubscribableEventSource eventSource() {
        return eventSource;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        super.describeTo(descriptor);
        descriptor.describeProperty("eventSource", eventSource);
    }
}
