/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.saga.ResourceInjector;
import org.axonframework.eventhandling.saga.repository.NoResourceInjector;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.Serializer;

import java.util.List;
import java.util.function.Supplier;

/**
 * Interface describing the Global Configuration for Axon components. It provides access to the components configured,
 * such as the Command Bus and Event Bus.
 * <p>
 * Note that certain components in the Configuration may need to be started. Therefore, before using any of the
 * components provided by this configuration, ensure that {@link #start()} has been invoked.
 */
public interface Configuration {

    /**
     * Retrieves the Event Bus defined in this Configuration.
     *
     * @return the Event Bus defined in this Configuration
     */
    default EventBus eventBus() {
        return getComponent(EventBus.class);
    }

    /**
     * Returns the Event Store in this Configuration, if it is defined. If no Event Store is defined (but an Event Bus
     * instead), this method throws an {@link AxonConfigurationException}.
     *
     * @return the Event Store defined in this Configuration
     */
    default EventStore eventStore() {
        EventBus eventBus = eventBus();
        if (!(eventBus instanceof EventStore)) {
            throw new AxonConfigurationException("A component is requesting an Event Store, however, there is none configured");
        }
        return (EventStore) eventBus;
    }

    /**
     * Returns the Command Bus defined in this Configuration. Note that this Configuration should be started (see
     * {@link #start()}) before sending Commands over the Command Bus.
     *
     * @return the CommandBus defined in this configuration
     */
    default CommandBus commandBus() {
        return getComponent(CommandBus.class);
    }

    /**
     * Returns the ResourceInjector used to provide resources to Saga instances.
     *
     * @return the ResourceInjector used to provide resources to Saga instances
     */
    default ResourceInjector resourceInjector() {
        return getComponent(ResourceInjector.class, () -> NoResourceInjector.INSTANCE);
    }

    /**
     * Returns the Command Gateway defined in this Configuration. Note that this Configuration should be started (see
     * {@link #start()}) before sending Commands using this Command Gateway.
     *
     * @return the CommandGateway defined in this configuration
     */
    default CommandGateway commandGateway() {
        return getComponent(CommandGateway.class);
    }

    /**
     * Returns the Repository configured for the given {@code aggregateType}.
     *
     * @param aggregateType The aggregate type to find the repository for
     * @param <T>           The aggregate type
     * @return the repository from which aggregates of the given type can be loaded
     */
    <T> Repository<T> repository(Class<T> aggregateType);

    /**
     * Returns the Component declared under the given {@code componentType}, typically the interface the component
     * implements.
     *
     * @param componentType The type of component
     * @param <T>           The type of component
     * @return the component registered for the given type, or {@code null} if no such component exists
     */
    default <T> T getComponent(Class<T> componentType) {
        return getComponent(componentType, () -> null);
    }

    /**
     * Returns the Component declared under the given {@code componentType}, typically the interface the component
     * implements, reverting to the given {@code defaultImpl} if no such component is defined.
     * <p>
     * When no component was previously registered, the default is then configured as the component for the given type.
     *
     * @param componentType The type of component
     * @param defaultImpl   The supplier of the default to return if no component was registered
     * @param <T>           The type of component
     * @return the component registered for the given type, or the value returned by the {@code defaultImpl} supplier,
     * if no component was registered
     */
    <T> T getComponent(Class<T> componentType, Supplier<T> defaultImpl);

    /**
     * Returns the message monitor configured for a component of given {@code componentType} and {@code componentName}.
     *
     * @param componentType The type of component to return the monitor for
     * @param componentName The name of the component
     * @param <M>           The type of message the monitor can deal with
     * @return The monitor to be used for the described component
     */
    <M extends Message<?>> MessageMonitor<? super M> messageMonitor(Class<?> componentType, String componentName);

    /**
     * Returns the serializer defined in this Configuration
     *
     * @return the serializer defined in this Configuration
     */
    default Serializer serializer() {
        return getComponent(Serializer.class);
    }

    /**
     * Starts this configuration. All components defined in this Configuration will be started.
     */
    void start();

    /**
     * Shuts down the components defined in this Configuration
     */
    void shutdown();

    /**
     * Returns the Correlation Data Providers defined in this Configuration.
     *
     * @return the Correlation Data Providers defined in this Configuration
     */
    List<CorrelationDataProvider> correlationDataProviders();

    /**
     * Returns the Parameter Resolver Factory defined in this Configuration
     *
     * @return the Parameter Resolver Factory defined in this Configuration
     */
    default ParameterResolverFactory parameterResolverFactory() {
        return getComponent(ParameterResolverFactory.class);
    }

    /**
     * Registers a handler to be executed when this Configuration is started.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already started is undefined.
     *
     * @param startHandler The handler to execute when the configuration is started
     * @see #start()
     * @see #onShutdown(Runnable)
     */
    void onStart(Runnable startHandler);

    /**
     * Registers a handler to be executed when the Configuration is shut down.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already shut down is undefined.
     *
     * @param shutdownHandler The handler to execute when the Configuration is shut down
     * @see #shutdown()
     * @see #onStart(Runnable)
     */
    void onShutdown(Runnable shutdownHandler);
}
