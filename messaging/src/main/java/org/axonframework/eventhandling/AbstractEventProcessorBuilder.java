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
import org.apache.commons.lang3.NotImplementedException;
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
 * Abstract Builder class to instantiate a {@link AsyncAbstractEventProcessor}.
 * <p>
 * The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
 * {@link NoOpMessageMonitor} and the {@link EventProcessorSpanFactory} defaults to
 * {@link DefaultEventProcessorSpanFactory} backed by a {@link NoOpSpanFactory}. The Event Processor {@code name} and
 * {@link EventHandlerInvoker} are <b>hard requirements</b> and as such should be provided.
 */
public abstract class AbstractEventProcessorBuilder {

    protected String name;
    private EventHandlerInvoker eventHandlerInvoker;
    private ErrorHandler errorHandler = PropagatingErrorHandler.INSTANCE;
    private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
    private EventProcessorSpanFactory spanFactory = DefaultEventProcessorSpanFactory.builder()
                                                                                    .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                    .build();

    /**
     * Sets the {@code name} of this {@link EventProcessor} implementation.
     *
     * @param name a {@link String} defining this {@link EventProcessor} implementation
     * @return the current Builder instance, for fluent interfacing
     */
    public AbstractEventProcessorBuilder name(@Nonnull String name) {
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
     */
    public AbstractEventProcessorBuilder eventHandlerInvoker(@Nonnull EventHandlerInvoker eventHandlerInvoker) {
        assertNonNull(eventHandlerInvoker, "EventHandlerInvoker may not be null");
        this.eventHandlerInvoker = eventHandlerInvoker;
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
    public AbstractEventProcessorBuilder errorHandler(@Nonnull ErrorHandler errorHandler) {
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
    public AbstractEventProcessorBuilder messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
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
    public AbstractEventProcessorBuilder spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
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
//        assertNonNull(eventHandlerInvoker, "The EventHandlerInvoker is a hard requirement and should be provided");
    }

    private void assertEventProcessorName(String eventProcessorName, String exceptionMessage) {
        assertThat(eventProcessorName, name -> Objects.nonNull(name) && !"".equals(name), exceptionMessage);
    }

    public String name() {
        return name;
    }

    public EventHandlerInvoker eventHandlerInvoker() {
        return eventHandlerInvoker;
    }

    public ErrorHandler errorHandler() {
        return errorHandler;
    }

    public MessageMonitor<? super EventMessage<?>> messageMonitor() {
        return messageMonitor;
    }

    public EventProcessorSpanFactory spanFactory() {
        return spanFactory;
    }

    public EventHandlingComponent eventHandlingComponent() {
        throw new NotImplementedException("Not implemented in AbstractEventProcessorBuilder");
    }
}
