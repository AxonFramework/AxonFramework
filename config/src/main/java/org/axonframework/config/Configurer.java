/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Entry point of the Axon Configuration API.
 * <p>
 * Using {@link DefaultConfigurer#defaultConfiguration()}, you will get a Configurer instance with default components
 * configured. You will need to register your Aggregates (using {@link #configureAggregate(AggregateConfiguration)} and
 * provide a repository implementation for each of them, or if you wish to use event sourcing, register your aggregates
 * through {@link #configureAggregate(Class)} and configure an Event Store ({@link #configureEventStore(Function)} or
 * {@link #configureEmbeddedEventStore(Function)}).
 *
 * @see DefaultConfigurer
 */
public interface Configurer {

    /**
     * Registers an upcaster to be used to upcast Events to a newer version
     *
     * @param upcasterBuilder The function that returns an EventUpcaster based on the configuration
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer registerEventUpcaster(Function<Configuration, EventUpcaster> upcasterBuilder);

    /**
     * Configures the Message Monitor to use for the Message processing components in this configuration, unless more
     * specific configuration based on the component's type, or type and name is available. The builder function
     * receives the type of component as well as its name as input, and is expected to return a MessageMonitor
     * instance to be used by that type of component.
     *
     * @param messageMonitorFactoryBuilder The MessageMonitor builder function
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureMessageMonitor(Function<Configuration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactoryBuilder);

    /**
     * Configures the builder function to create the Message Monitor for the Message processing components in this
     * configuration that match the given componentType, unless more specific configuration based on both type and name
     * is available.
     * <p>
     * <p>A component matches componentType if componentType is assignable from the component's class. If a component
     * matches multiple types, and the types derive from each other, the configuration from the most derived type is
     * used. If the matching types do not derive from each other, the result is unspecified.</p>
     * <p>
     * <p>For example: in case a monitor is configured for {@link CommandBus} and another monitor is configured for
     * {@link org.axonframework.commandhandling.SimpleCommandBus SimpleCommandBus}), components of type
     * {@link org.axonframework.commandhandling.AsynchronousCommandBus AsynchronousCommandBus} will use the monitor
     * configured for the SimpleCommandBus.</p>
     * <p>
     * <p>A component's name matches componentName if they are identical; i.e. they are compared case sensitively.</p>
     *
     * @param componentType         The declared type of the component
     * @param messageMonitorBuilder The builder function to use
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureMessageMonitor(Class<?> componentType,
                                               Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
        return configureMessageMonitor(componentType,
                                       (configuration, type, name) -> messageMonitorBuilder.apply(configuration));
    }

    /**
     * Configures the factory to create the Message Monitor for the Message processing components in this configuration
     * that match the given componentType, unless more specific configuration based on both type and name is available.
     * <p>
     * <p>A component matches componentType if componentType is assignable from the component's class. If a component
     * matches multiple types, and the types derive from each other, the configuration from the most derived type is
     * used. If the matching types do not derive from each other, the result is unspecified.</p>
     * <p>
     * <p>For example: in case a monitor is configured for {@link CommandBus} and another monitor is configured for
     * {@link org.axonframework.commandhandling.SimpleCommandBus SimpleCommandBus}), components of type
     * {@link org.axonframework.commandhandling.AsynchronousCommandBus AsynchronousCommandBus} will use the monitor
     * configured for the SimpleCommandBus.</p>
     * <p>
     * <p>A component's name matches componentName if they are identical; i.e. they are compared case sensitively.</p>
     *
     * @param componentType         The declared type of the component
     * @param messageMonitorFactory The factory to use
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureMessageMonitor(Class<?> componentType, MessageMonitorFactory messageMonitorFactory);

    /**
     * Configures the builder function to create the Message Monitor for the Message processing components in this
     * configuration that match the given class and name.
     * <p>
     * <p>A component matches componentType if componentType is assignable from the component's class. If a component
     * matches multiple types, and the types derive from each other, the configuration from the most derived type is
     * used. If the matching types do not derive from each other, the result is unspecified.</p>
     * <p>
     * <p>For example: in case a monitor is configured for {@link CommandBus} and another monitor is configured for
     * {@link org.axonframework.commandhandling.SimpleCommandBus SimpleCommandBus}), components of type
     * {@link org.axonframework.commandhandling.AsynchronousCommandBus AsynchronousCommandBus} will use the monitor
     * configured for the SimpleCommandBus.</p>
     * <p>
     * <p>A component's name matches componentName if they are identical; i.e. they are compared case sensitively.</p>
     *
     * @param componentType         The declared type of the component
     * @param componentName         The name of the component
     * @param messageMonitorBuilder The builder function to use
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureMessageMonitor(Class<?> componentType, String componentName,
                                               Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
        return configureMessageMonitor(componentType,
                                       componentName,
                                       (configuration, type, name) -> messageMonitorBuilder.apply(configuration));
    }

    /**
     * Configures the factory create the Message Monitor for those Message processing components in this configuration
     * that match the given class and name.
     * <p>
     * <p>A component matches componentType if componentType is assignable from the component's class. If a component
     * matches multiple types, and the types derive from each other, the configuration from the most derived type is
     * used. If the matching types do not derive from each other, the result is unspecified.</p>
     * <p>
     * <p>For example: in case a monitor is configured for {@link CommandBus} and another monitor is configured for
     * {@link org.axonframework.commandhandling.SimpleCommandBus SimpleCommandBus}), components of type
     * {@link org.axonframework.commandhandling.AsynchronousCommandBus AsynchronousCommandBus} will use the monitor
     * configured for the SimpleCommandBus.</p>
     * <p>
     * <p>A component's name matches componentName if they are identical; i.e. they are compared case sensitively.</p>
     *
     * @param componentType         The declared type of the component
     * @param componentName         The name of the component
     * @param messageMonitorFactory The factory to use
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureMessageMonitor(Class<?> componentType, String componentName,
                                       MessageMonitorFactory messageMonitorFactory);

    /**
     * Configures the CorrelationDataProviders that Message processing components should use to attach correlation data
     * to outgoing messages. The builder function receives the Configuration as input and is expected to return a list
     * or CorrelationDataProviders.
     *
     * @param correlationDataProviderBuilder the builder function returning the CorrelationDataProvider list
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureCorrelationDataProviders(Function<Configuration, List<CorrelationDataProvider>> correlationDataProviderBuilder);

    /**
     * Registers an Axon module with this configuration. The module is initialized when the configuration is created and
     * has access to the global configuration when initialized.
     * <p>
     * Typically, modules are registered for Event Handling components or Sagas.
     *
     * @param module The module to register
     * @return the current instance of the Configurer, for chaining purposes
     * @see SagaConfiguration
     */
    Configurer registerModule(ModuleConfiguration module);

    /**
     * Registers a component which should be made available to other components or modules in this Configuration. The
     * builder function gets this configuration as input, and is expected to provide the component as output.
     * <p>
     * Where possible, it is recommended to use the explicit {@code configure...} and {@code register...} methods.
     *
     * @param componentType    The declared type of the component, typically an interface
     * @param componentBuilder The builder function of this component
     * @param <C>              The type of component
     * @return the current instance of the Configurer, for chaining purposes
     */
    <C> Configurer registerComponent(Class<C> componentType, Function<Configuration, ? extends C> componentBuilder);

    /**
     * Registers a command handler bean with this configuration. The bean may be of any type. The actual command handler
     * methods will be detected based on the annotations present on the bean's methods.
     * <p>
     * The builder function receives the Configuration as input, and is expected to return a fully initialized instance
     * of the command handler bean.
     *
     * @param annotatedCommandHandlerBuilder The builder function of the Command Handler bean
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer registerCommandHandler(Function<Configuration, Object> annotatedCommandHandlerBuilder) {
        return registerCommandHandler(0, annotatedCommandHandlerBuilder);
    }

    /**
     * Registers a command handler bean with this configuration. The bean may be of any type. The actual command handler
     * methods will be detected based on the annotations present on the bean's methods.
     * <p>
     * The builder function receives the Configuration as input, and is expected to return a fully initialized instance
     * of the command handler bean.
     *
     * @param annotatedCommandHandlerBuilder The builder function of the Command Handler bean
     * @param phase                          defines a phase in which the command handler builder will be invoked during
     *                                       {@link Configuration#start()} and {@link Configuration#shutdown()}. When
     *                                       starting the configuration handlers are ordered in ascending, when shutting
     *                                       down the configuration, descending order is used.
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer registerCommandHandler(int phase, Function<Configuration, Object> annotatedCommandHandlerBuilder);

    /**
     * Registers a query handler bean with this configuration. The bean may be of any type. The actual query handler
     * methods will be detected based on the annotations present on the bean's methods.
     * <p>
     * The builder function receives the Configuration as input, and is expected to return a fully initialized instance
     * of the query handler bean.
     *
     * @param annotatedQueryHandlerBuilder The builder function of the Query Handler bean
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer registerQueryHandler(Function<Configuration, Object> annotatedQueryHandlerBuilder) {
        return registerQueryHandler(0, annotatedQueryHandlerBuilder);
    }

    /**
     * Registers a query handler bean with this configuration. The bean may be of any type. The actual query handler
     * methods will be detected based on the annotations present on the bean's methods.
     * <p>
     * The builder function receives the Configuration as input, and is expected to return a fully initialized instance
     * of the query handler bean.
     *
     * @param annotatedQueryHandlerBuilder The builder function of the Query Handler bean
     * @param phase                        defines a phase in which the query handler builder will be invoked during
     *                                     {@link Configuration#start()} and {@link Configuration#shutdown()}. When
     *                                     starting the configuration handlers are ordered in ascending, when shutting
     *                                     down the configuration, descending order is used.
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer registerQueryHandler(int phase, Function<Configuration, Object> annotatedQueryHandlerBuilder);

    /**
     * Configures an Embedded Event Store which uses the given Event Storage Engine to store its events. The builder
     * receives the Configuration as input and is expected to return a fully initialized {@link EventStorageEngine}
     * instance.
     *
     * @param storageEngineBuilder The builder function for the {@link EventStorageEngine}
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureEmbeddedEventStore(Function<Configuration, EventStorageEngine> storageEngineBuilder);

    /**
     * Configures the given Event Store to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link EventStore}
     * instance.
     *
     * @param eventStoreBuilder The builder function for the {@link EventStore}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureEventStore(Function<Configuration, EventStore> eventStoreBuilder) {
        return registerComponent(EventBus.class, eventStoreBuilder);
    }

    /**
     * Configures the given Event Bus to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link EventBus}
     * instance.
     * <p>
     * Note that this builder should not be used when an Event Store is configured. Since Axon 3, the Event Store will
     * act as Event Bus implementation as well.
     *
     * @param eventBusBuilder The builder function for the {@link EventBus}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureEventBus(Function<Configuration, EventBus> eventBusBuilder) {
        return registerComponent(EventBus.class, eventBusBuilder);
    }

    /**
     * Configures the given Command Bus to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link CommandBus}
     * instance.
     *
     * @param commandBusBuilder The builder function for the {@link CommandBus}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureCommandBus(Function<Configuration, CommandBus> commandBusBuilder) {
        return registerComponent(CommandBus.class, commandBusBuilder);
    }

    /**
     * Configures the given Query Bus to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link QueryBus}
     * instance.
     *
     * @param queryBusBuilder The builder function for the {@link QueryBus}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureQueryBus(Function<Configuration, QueryBus> queryBusBuilder) {
        return registerComponent(QueryBus.class, queryBusBuilder);
    }

    /**
     * Configures the given Query Update Emitter to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link QueryUpdateEmitter} instance.
     *
     * @param queryUpdateEmitterBuilder The builder function for the {@link QueryUpdateEmitter}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureQueryUpdateEmitter(
            Function<Configuration, QueryUpdateEmitter> queryUpdateEmitterBuilder) {
        return registerComponent(QueryUpdateEmitter.class, queryUpdateEmitterBuilder);
    }

    /**
     * Configures the given Serializer to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link Serializer}
     * instance.
     *
     * @param serializerBuilder The builder function for the {@link Serializer}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureSerializer(Function<Configuration, Serializer> serializerBuilder) {
        return registerComponent(Serializer.class, serializerBuilder);
    }

    /**
     * Configures the given event Serializer to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link org.axonframework.serialization.Serializer} instance.
     * <p/>
     * This Serializer is specifically used to serialize EventMessage payload and metadata.
     *
     * @param eventSerializerBuilder The builder function for the {@link org.axonframework.serialization.Serializer}.
     * @return The current instance of the Configurer, for chaining purposes.
     */
    Configurer configureEventSerializer(Function<Configuration, Serializer> eventSerializerBuilder);

    /**
     * Configures the given event Serializer to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link org.axonframework.serialization.Serializer} instance.
     * <p/>
     * This Serializer is specifically used to serialize Message payload and Metadata.
     *
     * @param messageSerializerBuilder The builder function for the {@link org.axonframework.serialization.Serializer}.
     * @return The current instance of the Configurer, for chaining purposes.
     */
    Configurer configureMessageSerializer(Function<Configuration, Serializer> messageSerializerBuilder);

    /**
     * Configures the given Transaction Manager to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link TransactionManager}
     * instance.
     *
     * @param transactionManagerBuilder The builder function for the {@link TransactionManager}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureTransactionManager(Function<Configuration, TransactionManager> transactionManagerBuilder) {
        return registerComponent(TransactionManager.class, transactionManagerBuilder);
    }

    /**
     * Configures the given Resource Injector to use for Sagas in this configuration. The builder receives the
     * Configuration as input and is expected to return a fully initialized {@link ResourceInjector} instance.
     *
     * @param resourceInjectorBuilder The builder function for the {@link ResourceInjector}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureResourceInjector(Function<Configuration, ResourceInjector> resourceInjectorBuilder) {
        return registerComponent(ResourceInjector.class, resourceInjectorBuilder);
    }

    /**
     * Configures an Aggregate in this configuration based on the given {@code aggregateConfiguration}. This method
     * allows for more fine-grained configuration compared to the {@link #configureAggregate(Class)} method.
     *
     * @param aggregateConfiguration The instance describing the configuration of an Aggregate
     * @param <A>                    The type of aggregate the configuration is for
     * @return the current instance of the Configurer, for chaining purposes
     * @see AggregateConfigurer
     */
    <A> Configurer configureAggregate(AggregateConfiguration<A> aggregateConfiguration);

    /**
     * Configures an Aggregate using default settings. This means the aggregate is expected to be Event Sourced if an
     * Event Store present in the configuration. Otherwise, an explicit repository must be configured and the
     * {@link #configureAggregate(AggregateConfiguration)} must be used to register the aggregate.
     *
     * @param aggregate The aggregate type to register with the Configuration
     * @param <A>       The type of aggregate
     * @return the current instance of the Configurer, for chaining purposes
     */
    default <A> Configurer configureAggregate(Class<A> aggregate) {
        return configureAggregate(AggregateConfigurer.defaultConfiguration(aggregate));
    }

    /**
     * Registers the definition of a Handler class. Defaults to annotation based recognition of handler methods.
     *
     * @param handlerDefinitionClass A function providing the definition based on the current Configuration as well
     *                               as the class being inspected.
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer registerHandlerDefinition(BiFunction<Configuration, Class, HandlerDefinition> handlerDefinitionClass);

    /**
     * Retrievee the {@link EventProcessingConfigurer} registered as a module with this Configurer. If there aren't
     * any, it will create an {@link EventProcessingModule} and register it as a module. If there are multiple,
     * an {@link AxonConfigurationException} is thrown.
     *
     * @return an instance of Event Processing Configurer
     *
     * @throws AxonConfigurationException thrown if there are multiple {@link EventProcessingConfigurer}s
     */
    EventProcessingConfigurer eventProcessing() throws AxonConfigurationException;

    /**
     * Locates the {@link EventProcessingConfigurer} registered as a module with this Configurer and provides it to the
     * given consumer for configuration. If there aren't any pre-registered instances of
     * {@link EventProcessingConfigurer}, it will create an {@link EventProcessingModule} and register it as a module.
     * If there are multiple, an {@link AxonConfigurationException} is thrown.
     *
     * This method is identical to using {@link #eventProcessing()}, except that this variant allows for easier fluent
     * interfacing.
     *
     * @param eventProcessingConfigurer a consumer to configure the
     * @return an instance of Event Processing Configurer
     *
     * @throws AxonConfigurationException thrown if there are multiple {@link EventProcessingConfigurer}s
     */
    default Configurer eventProcessing(Consumer<EventProcessingConfigurer> eventProcessingConfigurer) throws AxonConfigurationException {
        eventProcessingConfigurer.accept(eventProcessing());
        return this;
    }

    /**
     * Returns the completely initialized Configuration built using this configurer. It is not recommended to change
     * any configuration on this Configurer once this method is called.
     *
     * @return the fully initialized Configuration
     */
    Configuration buildConfiguration();

    /**
     * Builds the configuration and starts it immediately. It is not recommended to change any configuration on this
     * Configurer once this method is called.
     *
     * @return The started configuration
     */
    default Configuration start() {
        Configuration configuration = buildConfiguration();
        configuration.start();
        return configuration;
    }
}
