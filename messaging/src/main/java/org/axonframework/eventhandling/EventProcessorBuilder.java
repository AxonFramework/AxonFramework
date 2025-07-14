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
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanFactory;

import java.util.Objects;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Abstract Builder class to instantiate an {@link EventProcessor} implementation.
 * <p>
 * The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
 * {@link NoOpMessageMonitor} and the {@link EventProcessorSpanFactory} defaults to
 * {@link DefaultEventProcessorSpanFactory} backed by a {@link NoOpSpanFactory}. The Event Processor {@code name} and
 * {@link EventHandlingComponent} are <b>hard requirements</b> and as such should be provided.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class EventProcessorBuilder {

    protected String name;
    private EventHandlingComponent eventHandlingComponent;
    protected ErrorHandler errorHandler = PropagatingErrorHandler.INSTANCE;
    protected MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
    protected EventProcessorSpanFactory spanFactory = DefaultEventProcessorSpanFactory.builder()
                                                                                      .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                      .build();

    /**
     * Sets the {@code name} of this {@link EventProcessor} implementation.
     *
     * @param name a {@link String} defining this {@link EventProcessor} implementation
     * @return the current Builder instance, for fluent interfacing
     */
    public EventProcessorBuilder name(@Nonnull String name) {
        assertEventProcessorName(name, "The EventProcessor name may not be null or empty");
        this.name = name;
        return this;
    }

    /**
     * Sets the {@link EventHandlerInvoker} which will handle all the individual {@link EventMessage}s.
     *
     * @param eventHandlerInvoker the {@link EventHandlerInvoker} which will handle all the individual
     *                            {@link EventMessage}s
     * @return the current Builder instance, for fluent interfacing
     * @deprecated in favor of {@link #eventHandlingComponent(EventHandlingComponent)}
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public EventProcessorBuilder eventHandlerInvoker(@Nonnull EventHandlerInvoker eventHandlerInvoker) {
        assertNonNull(eventHandlerInvoker, "EventHandlerInvoker may not be null");
        return eventHandlingComponent(new LegacyEventHandlingComponent(eventHandlerInvoker));
    }

    /**
     * Sets the {@link EventHandlingComponent} which will handle all the individual {@link EventMessage}s.
     *
     * @param eventHandlingComponent the {@link EventHandlingComponent} which will handle all the individual
     *                               {@link EventMessage}s
     * @return the current Builder instance, for fluent interfacing
     */
    public EventProcessorBuilder eventHandlingComponent(@Nonnull EventHandlingComponent eventHandlingComponent) {
        assertNonNull(eventHandlingComponent, "EventHandlingComponent may not be null");
        this.eventHandlingComponent = eventHandlingComponent;
        return this;
    }

    /**
     * Sets the {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception during processing. Defaults
     * to a {@link PropagatingErrorHandler}.
     *
     * @param errorHandler the {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception during
     *                     processing
     * @return the current Builder instance, for fluent interfacing
     */
    public EventProcessorBuilder errorHandler(@Nonnull ErrorHandler errorHandler) {
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
     * @return the current Builder instance, for fluent interfacing
     */
    public EventProcessorBuilder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
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
     * @return The current Builder instance, for fluent interfacing.
     */
    public EventProcessorBuilder spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
        assertNonNull(spanFactory, "SpanFactory may not be null");
        this.spanFactory = spanFactory;
        return this;
    }

    /**
     * Validates whether the fields contained in this Builder are set accordingly.
     *
     * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
     *                                    specifications
     */
    protected void validate() throws AxonConfigurationException {
        assertEventProcessorName(name, "The EventProcessor name is a hard requirement and should be provided");
        assertNonNull(eventHandlingComponent, "The EventHandlingComponent is a hard requirement and should be provided");
    }

    private void assertEventProcessorName(String eventProcessorName, String exceptionMessage) {
        assertThat(eventProcessorName, name -> Objects.nonNull(name) && !"".equals(name), exceptionMessage);
    }

    /**
     * Returns the name of this {@link EventProcessor} implementation.
     *
     * @return The {@link String} defining this {@link EventProcessor} implementation's name.
     */
    public String name() {
        return name;
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
    public MessageMonitor<? super EventMessage<?>> messageMonitor() {
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
     * Returns the {@link EventHandlingComponent} which handles all the individual {@link EventMessage}s.
     *
     * @return The {@link EventHandlingComponent} for this {@link EventProcessor} implementation.
     */
    public EventHandlingComponent eventHandlingComponent() {
        return eventHandlingComponent;
    }
}
