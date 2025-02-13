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

package org.axonframework.configuration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryUpdateEmitter;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Interface describing the Global Configuration for Axon components. It provides access to the components configured,
 * such as the Command Bus and Event Bus.
 * <p>
 * Note that certain components in the Configuration may need to be started. Therefore, before using any of the
 * components provided by this configuration, ensure that {@link #start()} has been invoked.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public interface NewConfiguration extends LifecycleOperations {

    /**
     * TODO the following configuration methods no longer reside here:
     * - TagsConfiguration tags()
     * - SpanFactory spanFactory()
     * - <A> AggregateConfiguration<A> aggregateConfiguration(@Nonnull Class<A> aggregateType)
     * - <A> Repository<A> repository(@Nonnull Class<A> aggregateType)<A> AggregateFactory<A> aggregateFactory(@Nonnull Class<A> aggregateType)
     * - <M extends Message<?>> MessageMonitor<? super M> messageMonitor(@Nonnull Class<?> componentType, @Nonnull String componentName);
     * - Serializer serializer()
     * - Serializer eventSerializer()
     * - Serializer messageSerializer()
     * - List<CorrelationDataProvider> correlationDataProviders();
     * - EventScheduler eventScheduler()
     * - DeadlineManager deadlineManager()
     * - Snapshotter snapshotter()
     * - ScopeAwareProvider scopeAwareProvider()
     * - SnapshotFilter snapshotFilter()
     * - ResourceInjector resourceInjector()
     * - EventProcessingConfiguration eventProcessingConfiguration() throws AxonConfigurationException
     * - ParameterResolverFactory parameterResolverFactory()
     * - HandlerDefinition handlerDefinition(Class<?> inspectedType);
     * - EventUpcasterChain upcasterChain();
     */

    /**
     * Retrieves the Event Bus defined in this Configuration.
     *
     * @return the Event Bus defined in this Configuration
     */
    default EventBus eventBus() {
        return getComponent(EventBus.class);
    }

    /**
     * Returns the lifecycle registry for this configuration. Typically, this is just an adapter around the
     * configuration itself to register individual handler methods.
     *
     * @return the lifecycle registry for this configuration
     */
    default Lifecycle.LifecycleRegistry lifecycleRegistry() {
        return new Lifecycle.LifecycleRegistry() {
            @Override
            public void onStart(int phase, @Nonnull Lifecycle.LifecycleHandler action) {
                NewConfiguration.this.onStart(phase, action::run);
            }

            @Override
            public void onShutdown(int phase, @Nonnull Lifecycle.LifecycleHandler action) {
                NewConfiguration.this.onShutdown(phase, action::run);
            }
        };
    }

    /**
     * Finds all configuration modules of given {@code moduleType} within this configuration.
     *
     * @param moduleType The type of the configuration module
     * @param <T>        The type of the configuration module
     * @return configuration modules of {@code moduleType} defined in this configuration
     */
    @SuppressWarnings("unchecked")
    default <T extends Module> List<T> findModules(@Nonnull Class<T> moduleType) {
        return getModules().stream()
                           .filter(m -> m.isType(moduleType))
                           .map(m -> (T) m.unwrap())
                           .collect(Collectors.toList());
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

    default QueryBus queryBus() {
        return getComponent(QueryBus.class);
    }

    /**
     * Returns the Query Update Emitter in this Configuration. Note that this Configuration should be started (see
     * {@link #start()} before emitting updates over Query Update Emitter.
     *
     * @return the QueryUpdateEmitter defined in this configuration
     */
    default QueryUpdateEmitter queryUpdateEmitter() {
        return getComponent(QueryUpdateEmitter.class);
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
     * Returns the Query Gateway defined in this Configuration. Note that this Configuration should be started (see
     * {@link #start()}) before sending Queries using this Query Gateway.
     *
     * @return the QueryGateway defined in this configuration
     */
    default QueryGateway queryGateway() {
        return getComponent(QueryGateway.class);
    }

    /**
     * Returns the Event Gateway defined in this Configuration.
     *
     * @return the EventGateway defined in this configuration
     */
    default EventGateway eventGateway() {
        return getComponent(EventGateway.class);
    }

    /**
     * Returns the Component declared under the given {@code componentType}, typically the interface the component
     * implements.
     *
     * @param componentType The type of component
     * @param <T>           The type of component
     * @return the component registered for the given type, or {@code null} if no such component exists
     */
    default <T> T getComponent(@Nonnull Class<T> componentType) {
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
    <T> T getComponent(@Nonnull Class<T> componentType, @Nonnull Supplier<T> defaultImpl);

    /**
     * Starts this configuration. All components defined in this Configuration will be started.
     */
    void start();

    /**
     * Shuts down the components defined in this Configuration
     */
    void shutdown();

    /**
     * Returns all modules that have been registered with this Configuration.
     *
     * @return all modules that have been registered with this Configuration
     */
    List<Module> getModules();
}
