/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.tracing.SpanFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Entry point of the Axon Configuration API.
 * <p>
 * Using {@link DefaultConfigurer#defaultConfiguration()}, you will get a Configurer instance with default components
 * configured. You will need to register your Aggregates (using {@link #configureAggregate(AggregateConfiguration)} and
 * provide a repository implementation for each of them, or if you wish to use event sourcing, register your aggregates
 * through {@link #configureAggregate(Class)} and configure an Event Store ({@link #configureEventStore(Function)} or
 * {@link #configureEmbeddedEventStore(Function)}).
 *
 * @author Allard Buijze
 * @see DefaultConfigurer
 * @since 3.0
 */
public interface Configurer extends LifecycleOperations {

    /**
     * Registers an upcaster to be used to upcast Events to a newer version
     *
     * @param upcasterBuilder The function that returns an EventUpcaster based on the configuration
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer registerEventUpcaster(@Nonnull Function<Configuration, EventUpcaster> upcasterBuilder);

    /**
     * Configures the Message Monitor to use for the Message processing components in this configuration, unless more
     * specific configuration based on the component's type, or type and name is available. The builder function
     * receives the type of component as well as its name as input, and is expected to return a MessageMonitor instance
     * to be used by that type of component.
     *
     * @param messageMonitorFactoryBuilder The MessageMonitor builder function
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureMessageMonitor(
            @Nonnull Function<Configuration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactoryBuilder);

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
    default Configurer configureMessageMonitor(@Nonnull Class<?> componentType,
                                               @Nonnull Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
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
    Configurer configureMessageMonitor(@Nonnull Class<?> componentType,
                                       @Nonnull MessageMonitorFactory messageMonitorFactory);

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
    default Configurer configureMessageMonitor(@Nonnull Class<?> componentType, @Nonnull String componentName,
                                               @Nonnull Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
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
    Configurer configureMessageMonitor(@Nonnull Class<?> componentType, @Nonnull String componentName,
                                       @Nonnull MessageMonitorFactory messageMonitorFactory);

    /**
     * Configures the CorrelationDataProviders that Message processing components should use to attach correlation data
     * to outgoing messages. The builder function receives the Configuration as input and is expected to return a list
     * or CorrelationDataProviders.
     *
     * @param correlationDataProviderBuilder the builder function returning the CorrelationDataProvider list
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureCorrelationDataProviders(
            @Nonnull Function<Configuration, List<CorrelationDataProvider>> correlationDataProviderBuilder);

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
    Configurer registerModule(@Nonnull ModuleConfiguration module);

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
    <C> Configurer registerComponent(@Nonnull Class<C> componentType,
                                     @Nonnull Function<Configuration, ? extends C> componentBuilder);

    /**
     * Registers a command handler bean with this {@link Configurer}. The bean may be of any type. The actual command
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link org.axonframework.commandhandling.CommandHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link Configuration} as input, and is expected to return a fully initialized
     * instance of the command handler bean.
     *
     * @param commandHandlerBuilder the builder function of the command handler bean
     * @return the current instance of the {@link Configurer}, for chaining purposes
     */
    Configurer registerCommandHandler(@Nonnull Function<Configuration, Object> commandHandlerBuilder);

    /**
     * Registers a command handler bean with this {@link Configurer}. The bean may be of any type. The actual command
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link org.axonframework.commandhandling.CommandHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link Configuration} as input, and is expected to return a fully initialized
     * instance of the command handler bean.
     *
     * @param commandHandlerBuilder the builder function of the command handler bean
     * @param phase                 defines a phase in which the command handler builder will be invoked during {@link
     *                              Configuration#start()} and {@link Configuration#shutdown()}. When starting the
     *                              configuration handlers are ordered in ascending, when shutting down the
     *                              configuration, descending order is used.
     * @return the current instance of the {@link Configurer}, for chaining purposes
     * @deprecated in favor of {@link #registerCommandHandler(Function)}, since the {@code phase} of an annotated
     * handler should be defined through the {@link org.axonframework.lifecycle.StartHandler}/{@link
     * org.axonframework.lifecycle.ShutdownHandler} annotation.
     */
    @Deprecated
    default Configurer registerCommandHandler(int phase,
                                              @Nonnull Function<Configuration, Object> commandHandlerBuilder) {
        return registerCommandHandler(commandHandlerBuilder);
    }

    /**
     * Registers a query handler bean with this {@link Configurer}. The bean may be of any type. The actual query
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link org.axonframework.queryhandling.QueryHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link Configuration} as input, and is expected to return a fully initialized
     * instance of the query handler bean.
     *
     * @param queryHandlerBuilder the builder function of the query handler bean
     * @return the current instance of the {@link Configurer}, for chaining purposes
     */
    Configurer registerQueryHandler(@Nonnull Function<Configuration, Object> queryHandlerBuilder);

    /**
     * Registers a query handler bean with this {@link Configurer}. The bean may be of any type. The actual query
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link org.axonframework.queryhandling.QueryHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link Configuration} as input, and is expected to return a fully initialized
     * instance of the query handler bean.
     *
     * @param queryHandlerBuilder the builder function of the query handler bean
     * @param phase               defines a phase in which the query handler builder will be invoked during {@link
     *                            Configuration#start()} and {@link Configuration#shutdown()}. When starting the
     *                            configuration handlers are ordered in ascending, when shutting down the configuration,
     *                            descending order is used.
     * @return the current instance of the {@link Configurer}, for chaining purposes
     * @deprecated in favor of {@link #registerQueryHandler(Function)}, since the {@code phase} of an annotated handler
     * should be defined through the {@link org.axonframework.lifecycle.StartHandler}/{@link
     * org.axonframework.lifecycle.ShutdownHandler} annotation.
     */
    @Deprecated
    default Configurer registerQueryHandler(int phase, @Nonnull Function<Configuration, Object> queryHandlerBuilder) {
        return registerQueryHandler(queryHandlerBuilder);
    }

    /**
     * Registers a message handler bean with this configuration. The bean may be of any type. The actual message handler
     * methods will be detected based on the annotations present on the bean's methods. Message handling functions
     * annotated with {@link org.axonframework.commandhandling.CommandHandler}, {@link
     * org.axonframework.eventhandling.EventHandler} and {@link org.axonframework.queryhandling.QueryHandler} will be
     * taken into account.
     * <p>
     * The builder function receives the {@link Configuration} as input, and is expected to return a fully initialized
     * instance of the message handler bean.
     *
     * @param messageHandlerBuilder the builder function of the message handler bean
     * @return the current instance of the {@link Configurer}, for chaining purposes
     */
    Configurer registerMessageHandler(@Nonnull Function<Configuration, Object> messageHandlerBuilder);

    /**
     * Configures an Embedded Event Store which uses the given Event Storage Engine to store its events. The builder
     * receives the Configuration as input and is expected to return a fully initialized {@link EventStorageEngine}
     * instance.
     *
     * @param storageEngineBuilder The builder function for the {@link EventStorageEngine}
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer configureEmbeddedEventStore(@Nonnull Function<Configuration, EventStorageEngine> storageEngineBuilder);

    /**
     * Configures the given Event Store to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link EventStore} instance.
     *
     * @param eventStoreBuilder The builder function for the {@link EventStore}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureEventStore(@Nonnull Function<Configuration, EventStore> eventStoreBuilder) {
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
    default Configurer configureEventBus(@Nonnull Function<Configuration, EventBus> eventBusBuilder) {
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
    default Configurer configureCommandBus(@Nonnull Function<Configuration, CommandBus> commandBusBuilder) {
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
    default Configurer configureQueryBus(@Nonnull Function<Configuration, QueryBus> queryBusBuilder) {
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
            @Nonnull Function<Configuration, QueryUpdateEmitter> queryUpdateEmitterBuilder) {
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
    default Configurer configureSerializer(@Nonnull Function<Configuration, Serializer> serializerBuilder) {
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
    Configurer configureEventSerializer(@Nonnull Function<Configuration, Serializer> eventSerializerBuilder);

    /**
     * Configures the given event Serializer to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link org.axonframework.serialization.Serializer} instance.
     * <p/>
     * This Serializer is specifically used to serialize Message payload and Metadata.
     *
     * @param messageSerializerBuilder The builder function for the {@link org.axonframework.serialization.Serializer}.
     * @return The current instance of the Configurer, for chaining purposes.
     */
    Configurer configureMessageSerializer(@Nonnull Function<Configuration, Serializer> messageSerializerBuilder);

    /**
     * Configures the given Transaction Manager to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link TransactionManager}
     * instance.
     *
     * @param transactionManagerBuilder The builder function for the {@link TransactionManager}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureTransactionManager(
            @Nonnull Function<Configuration, TransactionManager> transactionManagerBuilder) {
        return registerComponent(TransactionManager.class, transactionManagerBuilder);
    }

    /**
     * Configures the given Resource Injector to use for Sagas in this configuration. The builder receives the
     * Configuration as input and is expected to return a fully initialized {@link ResourceInjector} instance.
     *
     * @param resourceInjectorBuilder The builder function for the {@link ResourceInjector}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureResourceInjector(
            @Nonnull Function<Configuration, ResourceInjector> resourceInjectorBuilder) {
        return registerComponent(ResourceInjector.class, resourceInjectorBuilder);
    }

    /**
     * Configures the given Tags Configuration to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link TagsConfiguration} instance.
     *
     * @param tagsBuilder The builder function for the {@link TagsConfiguration}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureTags(@Nonnull Function<Configuration, TagsConfiguration> tagsBuilder) {
        return registerComponent(TagsConfiguration.class, tagsBuilder);
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
    <A> Configurer configureAggregate(@Nonnull AggregateConfiguration<A> aggregateConfiguration);

    /**
     * Configures an Aggregate using default settings. This means the aggregate is expected to be Event Sourced if an
     * Event Store present in the configuration. Otherwise, an explicit repository must be configured and the
     * {@link #configureAggregate(AggregateConfiguration)} must be used to register the aggregate.
     *
     * @param aggregate The aggregate type to register with the Configuration
     * @param <A>       The type of aggregate
     * @return the current instance of the Configurer, for chaining purposes
     */
    default <A> Configurer configureAggregate(@Nonnull Class<A> aggregate) {
        return configureAggregate(AggregateConfigurer.defaultConfiguration(aggregate));
    }

    /**
     * Registers the definition of a Handler class. Defaults to annotation based recognition of handler methods.
     *
     * @param handlerDefinitionClass A function providing the definition based on the current Configuration as well
     *                               as the class being inspected.
     * @return the current instance of the Configurer, for chaining purposes
     */
    Configurer registerHandlerDefinition(
            @Nonnull BiFunction<Configuration, Class, HandlerDefinition> handlerDefinitionClass);

    /**
     * Registers a builder function for a {@link HandlerEnhancerDefinition} used during constructing of the default
     * {@link HandlerDefinition}.
     * <p>
     * Any number of handler enhancer builder functions can be registered through this method. Note that any
     * {@code HandlerEnhancerDefinitions} registered through this method are <b>ignored</b> for handlers matching the
     * type used in the {@link #registerHandlerDefinition(BiFunction)} method's lambda.
     *
     * @param handlerEnhancerBuilder A lambda constructing a {@link HandlerEnhancerDefinition} based on the
     *                               {@link Configuration}.
     * @return The current instance of the {@link Configurer}, for chaining purposes.
     */
    Configurer registerHandlerEnhancerDefinition(
            Function<Configuration, HandlerEnhancerDefinition> handlerEnhancerBuilder
    );

    /**
     * Registers a {@link Snapshotter} instance with this {@link Configurer}. Defaults to a {@link
     * org.axonframework.eventsourcing.AggregateSnapshotter} implementation.
     *
     * @param snapshotterBuilder the builder function for the {@link Snapshotter}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureSnapshotter(@Nonnull Function<Configuration, Snapshotter> snapshotterBuilder) {
        return registerComponent(Snapshotter.class, snapshotterBuilder);
    }

    /**
     * Registers a {@link DeadlineManager} instance with this {@link Configurer}. Defaults to a {@link
     * org.axonframework.deadline.SimpleDeadlineManager} implementation.
     *
     * @param deadlineManagerBuilder a builder function for the {@link DeadlineManager}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureDeadlineManager(
            @Nonnull Function<Configuration, DeadlineManager> deadlineManagerBuilder) {
        return registerComponent(DeadlineManager.class, deadlineManagerBuilder);
    }

    /**
     * Registers a {@link SpanFactory} instance with this {@link Configurer}. Defaults to a
     * {@link org.axonframework.tracing.NoOpSpanFactory} implementation.
     *
     * @param spanFactory a builder function for the {@link SpanFactory}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default Configurer configureSpanFactory(
            @Nonnull Function<Configuration, SpanFactory> spanFactory) {
        return registerComponent(SpanFactory.class, spanFactory);
    }

    /**
     * Retrieve the {@link EventProcessingConfigurer} registered as a module with this Configurer. If there aren't
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
    default Configurer eventProcessing(@Nonnull Consumer<EventProcessingConfigurer> eventProcessingConfigurer)
            throws AxonConfigurationException {
        eventProcessingConfigurer.accept(eventProcessing());
        return this;
    }

    /**
     * Registers a {@link Function} that builds an Event Handler instance.
     *
     * @param eventHandlerBuilder a {@link Function} that builds an Event Handler instance.
     * @return the current instance of the Configurer, for chaining purposes.
     */
    default Configurer registerEventHandler(@Nonnull Function<Configuration, Object> eventHandlerBuilder) {
        eventProcessing().registerEventHandler(eventHandlerBuilder);
        return this;
    }

    /**
     * Configures the timeout of each lifecycle phase. The Configurer invokes lifecycle phases during start-up and
     * shutdown of an application.
     * <p>
     * Note that if a lifecycle phase exceeds the configured {@code timeout} and {@code timeUnit} combination, the
     * Configurer will proceed with the following phase. A phase-skip is marked with a warn logging message, as the
     * chances are high this causes undesired side effects.
     * <p>
     * The default lifecycle phase timeout is five seconds.
     *
     * @param timeout  the amount of time to wait for lifecycle phase completion
     * @param timeUnit the unit in which the {@code timeout} is expressed
     * @return the current instance of the Configurer, for chaining purposes
     * @see org.axonframework.lifecycle.Phase
     * @see LifecycleHandler
     */
    default Configurer configureLifecyclePhaseTimeout(long timeout, TimeUnit timeUnit) {
        return this;
    }

    /**
     * Register an initialization handler which should be invoked prior to starting this {@link Configurer}.
     *
     * @param initHandler a {@link Consumer} of the configuration, to be ran upon initialization of the {@link
     *                    Configuration}
     */
    default void onInitialize(@Nonnull Consumer<Configuration> initHandler) {
        registerModule(initHandler::accept);
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
