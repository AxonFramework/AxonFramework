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

package org.axonframework.messaging.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.interception.HandlerInterceptorRegistry;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorHandler;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.monitoring.configuration.MessageMonitorRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Configuration class to be used for {@link EventProcessor} implementations.
 * <p>
 * The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler} and the {@link UnitOfWorkFactory} defaults
 * to the {@link SimpleUnitOfWorkFactory}
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class EventProcessorConfiguration implements DescribableComponent {

    protected final String processorName;
    protected ErrorHandler errorHandler = PropagatingErrorHandler.INSTANCE;
    protected UnitOfWorkFactory unitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);
    protected List<MessageHandlerInterceptor<? super EventMessage>> interceptors = new ArrayList<>();
    protected BiFunction<Class<? extends EventProcessor>, String, List<MessageHandlerInterceptor<? super EventMessage>>> interceptorBuilder =
            (processorType, name) -> new ArrayList<>();
    protected BiFunction<Class<? extends EventProcessor>, String, MessageMonitor<? super EventMessage>> monitorBuilder =
            (processorType, name) -> NoOpMessageMonitor.INSTANCE;

    /**
     * Constructs a new {@code EventProcessorConfiguration} with default values and retrieve global default values.
     * <p>
     * When the given {@code config} is {@code null}, the {@link MessageHandlerInterceptor MessageHandlerInterceptors}
     * and {@link MessageMonitor} will not be retrieved from the {@link HandlerInterceptorRegistry} and
     * {@link MessageMonitorRegistry} respectively.
     *
     * @param processorName the name of the processor this configuration is for
     * @param config        the config, used to retrieve global default values, like
     *                      {@link MessageHandlerInterceptor MessageHandlerInterceptors}, from
     */
    @Internal
    public EventProcessorConfiguration(@Nonnull String processorName,
                                       @Nullable Configuration config) {
        this.processorName = Assert.nonEmpty(processorName, "The processor name cannot be null or empty.");
        if (config != null) {
            this.interceptorBuilder = (procType, procName) -> config.getComponent(HandlerInterceptorRegistry.class)
                                                                    .eventInterceptors(config, procType, procName);
            this.monitorBuilder = (procType, procName) -> config.getComponent(MessageMonitorRegistry.class)
                                                                .eventMonitor(config, procType, procName);
        }
    }

    /**
     * Constructs a new {@code EventProcessorConfiguration} copying properties from the given configuration.
     *
     * @param base the {@code EventProcessorConfiguration} to copy properties from
     */
    @Internal
    public EventProcessorConfiguration(@Nonnull EventProcessorConfiguration base) {
        Objects.requireNonNull(base, "Base configuration may not be null");
        this.processorName = base.processorName;
        this.errorHandler = base.errorHandler();
        this.unitOfWorkFactory = base.unitOfWorkFactory();
        this.interceptors = new ArrayList<>(base.interceptors);
        this.interceptorBuilder = base.interceptorBuilder;
        this.monitorBuilder = base.monitorBuilder;
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
     * A {@link UnitOfWorkFactory} that spawns {@link UnitOfWork} used to process an event batch.
     *
     * @param unitOfWorkFactory A {@link UnitOfWorkFactory} that spawns {@link UnitOfWork}.
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
        descriptor.describeProperty("unitOfWorkFactory", unitOfWorkFactory);
        descriptor.describeProperty("interceptors", interceptors);
    }
}
