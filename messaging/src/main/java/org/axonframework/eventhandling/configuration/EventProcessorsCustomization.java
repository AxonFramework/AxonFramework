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
import org.axonframework.eventhandling.DefaultEventProcessorSpanFactory;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;

import java.util.Objects;

/**
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class EventProcessorsCustomization {

    protected ErrorHandler errorHandler = PropagatingErrorHandler.INSTANCE;
    protected MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
    protected EventProcessorSpanFactory spanFactory = DefaultEventProcessorSpanFactory.builder()
                                                                                    .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                    .build();

    public EventProcessorsCustomization() {
        // Default constructor
    }

    public EventProcessorsCustomization(@Nonnull EventProcessorsCustomization base) {
        Objects.requireNonNull(base, "Base configuration may not be null");
        this.errorHandler = base.errorHandler();
        this.messageMonitor = base.messageMonitor();
        this.spanFactory = base.spanFactory();
    }

    public EventProcessorsCustomization errorHandler(@Nonnull ErrorHandler errorHandler) {
        Objects.requireNonNull(errorHandler, "ErrorHandler may not be null");
        this.errorHandler = errorHandler;
        return this;
    }

    public ErrorHandler errorHandler() {
        return errorHandler;
    }

    public EventProcessorsCustomization messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
        Objects.requireNonNull(messageMonitor, "MessageMonitor may not be null");
        this.messageMonitor = messageMonitor;
        return this;
    }

    public MessageMonitor<? super EventMessage<?>> messageMonitor() {
        return messageMonitor;
    }

    public EventProcessorsCustomization spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
        Objects.requireNonNull(spanFactory, "EventProcessorSpanFactory may not be null");
        this.spanFactory = spanFactory;
        return this;
    }

    public EventProcessorSpanFactory spanFactory() {
        return spanFactory;
    }
}
