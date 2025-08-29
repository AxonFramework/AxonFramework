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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.processors.errorhandling.ErrorHandler;
import org.axonframework.eventhandling.processors.errorhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.processors.tracing.DefaultEventProcessorSpanFactory;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.tracing.EventProcessorSpanFactory;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Configuration class to be used for {@link EventProcessor} implementations.
 * <p>
 * The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
 * {@link NoOpMessageMonitor} and the {@link EventProcessorSpanFactory} defaults to
 * {@link DefaultEventProcessorSpanFactory} backed by a {@link NoOpSpanFactory} and the {@link UnitOfWorkFactory}
 * defaults to the {@link org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory}
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class EventProcessorConfiguration implements DescribableComponent {

    protected ErrorHandler errorHandler = PropagatingErrorHandler.INSTANCE;

    // TODO #3098 - Remove MessageMonitor from EventProcessorConfiguration, keep the usages just in some decorator around EventHandlingComponent
    protected MessageMonitor<? super EventMessage> messageMonitor = NoOpMessageMonitor.INSTANCE;

    // TODO #3098 - Remove EventProcessorSpanFactory from EventProcessorConfiguration, keep the usages just in some decorator around EventHandlingComponent
    protected EventProcessorSpanFactory spanFactory = DefaultEventProcessorSpanFactory.builder()
                                                                                      .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                      .build();
    private List<MessageHandlerInterceptor<EventMessage>> interceptors = new ArrayList<>();
    protected UnitOfWorkFactory unitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);

    /**
     * Constructs a new {@link EventProcessorConfiguration} with default values.
     */
    public EventProcessorConfiguration() {
        super();
    }

    /**
     * Constructs a new {@link EventProcessorConfiguration} copying properties from the given configuration.
     *
     * @param base The {@link EventProcessorConfiguration} to copy properties from.
     */
    public EventProcessorConfiguration(@Nonnull EventProcessorConfiguration base) {
        Objects.requireNonNull(base, "Base configuration may not be null");
        assertNonNull(base, "Base configuration may not be null");
        this.errorHandler = base.errorHandler();
        this.messageMonitor = base.messageMonitor();
        this.spanFactory = base.spanFactory();
        this.interceptors = base.interceptors();
        this.unitOfWorkFactory = base.unitOfWorkFactory();
    }

    /**
     * Sets the {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception during processing. Defaults
     * to a {@link PropagatingErrorHandler}.
     *
     * @param errorHandler the {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception during
     *                     processing
     * @return The current instance, for fluent interfacing.
     */
    public EventProcessorConfiguration errorHandler(@Nonnull ErrorHandler errorHandler) {
        assertNonNull(errorHandler, "ErrorHandler may not be null");
        this.errorHandler = errorHandler;
        return this;
    }

    /**
     * Sets the {@link MessageMonitor} to monitor {@link EventMessage}s before and after they're processed. Defaults to
     * a {@link NoOpMessageMonitor}.
     *
     * @param messageMonitor a {@link MessageMonitor} to monitor {@link EventMessage}s before and after they're
     *                       processed
     * @return The current instance, for fluent interfacing.
     */
    public EventProcessorConfiguration messageMonitor(@Nonnull MessageMonitor<? super EventMessage> messageMonitor) {
        assertNonNull(messageMonitor, "MessageMonitor may not be null");
        this.messageMonitor = messageMonitor;
        return this;
    }

    /**
     * Sets the {@link EventProcessorSpanFactory} implementation to use for providing tracing capabilities. Defaults to
     * a {@link DefaultEventProcessorSpanFactory} backed by a {@link NoOpSpanFactory} by default, which provides no
     * tracing capabilities.
     *
     * @param spanFactory The {@link SpanFactory} implementation
     * @return The current instance, for fluent interfacing.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public EventProcessorConfiguration spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
        assertNonNull(spanFactory, "SpanFactory may not be null");
        this.spanFactory = spanFactory;
        return this;
    }

    @Deprecated(since = "5.0.0", forRemoval = true)
    public EventProcessorConfiguration interceptors(
            @Nonnull List<MessageHandlerInterceptor<EventMessage>> interceptors) {
        assertNonNull(spanFactory, "interceptors may not be null");
        this.interceptors = interceptors;
        return this;
    }

    /**
     * A {@link UnitOfWorkFactory} that spawns {@link org.axonframework.messaging.unitofwork.UnitOfWork} used to process
     * an event batch.
     *
     * @param unitOfWorkFactory A {@link UnitOfWorkFactory} that spawns
     *                          {@link org.axonframework.messaging.unitofwork.UnitOfWork}.
     * @return The current instance, for fluent interfacing.
     */
    public EventProcessorConfiguration unitOfWorkFactory(@Nonnull UnitOfWorkFactory unitOfWorkFactory) {
        assertNonNull(unitOfWorkFactory, "UnitOfWorkFactory may not be null");
        this.unitOfWorkFactory = unitOfWorkFactory;
        return this;
    }

    /**
     * Validates whether the fields contained in this Builder are set accordingly.
     *
     * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
     *                                    specifications
     */
    protected void validate() throws AxonConfigurationException {
        assertNonNull(unitOfWorkFactory, "The UnitOfWorkFactory is a hard requirement and should be provided");
    }

    /**
     * Returns the {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception during processing.
     *
     * @return The {@link ErrorHandler} for this {@link EventProcessor} implementation.
     */
    public ErrorHandler errorHandler() {
        return errorHandler;
    }

    /**
     * Returns the {@link MessageMonitor} used to monitor {@link EventMessage}s before and after they're processed.
     *
     * @return The {@link MessageMonitor} for this {@link EventProcessor} implementation.
     */
    public MessageMonitor<? super EventMessage> messageMonitor() {
        return messageMonitor;
    }

    /**
     * Returns the {@link EventProcessorSpanFactory} implementation used for providing tracing capabilities.
     *
     * @return The {@link EventProcessorSpanFactory} for this {@link EventProcessor} implementation.
     */
    public EventProcessorSpanFactory spanFactory() {
        return spanFactory;
    }

    /**
     * Returns the list of {@link MessageHandlerInterceptor}s applied to event processing.
     *
     * @return The list of interceptors for this {@link EventProcessor} implementation.
     */
    public List<MessageHandlerInterceptor<EventMessage>> interceptors() {
        return interceptors;
    }

    /**
     * Returns the {@link UnitOfWorkFactory} used to create {@link UnitOfWork} instances for event processing.
     *
     * @return The {@link UnitOfWorkFactory} for this {@link EventProcessor} implementation.
     */
    public UnitOfWorkFactory unitOfWorkFactory() {
        return unitOfWorkFactory;
    }

    /**
     * Returns whether this configuration is for a streaming event processor.
     *
     * @return {@code false} for basic configuration, {@code true} for streaming configurations.
     */
    public boolean streaming() {
        return false;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("errorHandler", errorHandler);
        descriptor.describeProperty("messageMonitor", messageMonitor);
        descriptor.describeProperty("spanFactory", spanFactory);
        descriptor.describeProperty("interceptors", interceptors);
        descriptor.describeProperty("unitOfWorkFactory", unitOfWorkFactory);
    }
}
