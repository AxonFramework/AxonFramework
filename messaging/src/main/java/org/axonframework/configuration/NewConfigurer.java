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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.configuration.MessageHandler;
import org.axonframework.messaging.configuration.MessageHandlingComponent;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

/**
 * Entry point of the Axon Configuration API. TODO expand documentation
 *
 * @author Allard Buijze
 * @since 3.0.0
 */
public interface NewConfigurer extends LifecycleOperations {

    /**
     * TODO the following configurer methods no longer reside here:
     * - Configurer registerEventUpcaster(@Nonnull Function<Configuration, EventUpcaster> upcasterBuilder)
     * - Configurer configureMessageMonitor(@Nonnull Function<Configuration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactoryBuilder)
     * - Configurer configureMessageMonitor(@Nonnull Class<?> componentType, @Nonnull Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder)
     * - Configurer configureMessageMonitor(@Nonnull Class<?> componentType, @Nonnull MessageMonitorFactory messageMonitorFactory)
     * - Configurer configureMessageMonitor(@Nonnull Class<?> componentType, @Nonnull String componentName, @Nonnull Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
     * - Configurer configureMessageMonitor(@Nonnull Class<?> componentType, @Nonnull String componentName, @Nonnull MessageMonitorFactory messageMonitorFactory)
     * - Configurer configureCorrelationDataProviders(@Nonnull Function<Configuration, List<CorrelationDataProvider>> correlationDataProviderBuilder);
     * - Configurer configureSerializer(@Nonnull Function<Configuration, Serializer> serializerBuilder) {return registerComponent(Serializer.class, serializerBuilder)
     * - Configurer configureEventSerializer(@Nonnull Function<Configuration, Serializer> eventSerializerBuilder);
     * - Configurer configureMessageSerializer(@Nonnull Function<Configuration, Serializer> messageSerializerBuilder);
     * - Configurer configureTransactionManager(@Nonnull Function<Configuration, TransactionManager> transactionManagerBuilder)
     * - Configurer configureResourceInjector(@Nonnull Function<Configuration, ResourceInjector> resourceInjectorBuilder)
     * - Configurer configureTags(@Nonnull Function<Configuration, TagsConfiguration> tagsBuilder)
     * - <A> Configurer configureAggregate(@Nonnull AggregateConfiguration<A> aggregateConfiguration)
     * - <A> Configurer configureAggregate(@Nonnull Class<A> aggregate)
     * - Configurer configureSnapshotter(@Nonnull Function<Configuration, Snapshotter> snapshotterBuilder)
     * - Configurer configureDeadlineManager(@Nonnull Function<Configuration, DeadlineManager> deadlineManagerBuilder)
     * - Configurer configureSpanFactory(@Nonnull Function<Configuration, SpanFactory> spanFactory)
     * - Configurer configureEventStore(@Nonnull Function<NewConfiguration, EventStore> eventStoreBuilder)
     * - Configurer registerHandlerDefinition(@Nonnull BiFunction<NewConfiguration, Class, HandlerDefinition> handlerDefinitionClass)
     * - Configurer registerHandlerEnhancerDefinition(Function<NewConfiguration, HandlerEnhancerDefinition> handlerEnhancerBuilder)
     * - EventProcessingConfigurer eventProcessing() throws AxonConfigurationException;
     * - Configurer eventProcessing(@Nonnull Consumer<EventProcessingConfigurer> eventProcessingConfigurer) throws AxonConfigurationException
     * - Configurer registerEventHandler(@Nonnull Function<NewConfiguration, Object> eventHandlerBuilder)
     */

    /**
     * Registers a component which should be made available to other components or modules in this Configuration. The
     * builder function gets this configuration as input, and is expected to provide the component as output.
     * <p>
     * Where possible, it is recommended to use the explicit {@code configure...} and {@code register...} methods.
     *
     * @param type    The declared type of the component, typically an interface
     * @param componentBuilder The builder function of this component
     * @param <C>              The type of component
     * @return the current instance of the Configurer, for chaining purposes
     */
    <C> NewConfigurer registerComponent(@Nonnull Class<C> type,
                                        @Nonnull ComponentBuilder<C> componentBuilder);

    /**
     * TODO
     * @param type
     * @param componentBuilder
     * @return
     * @param <C>
     */
    <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                        @Nonnull ComponentDecorator<C> componentBuilder);

    /**
     * TODO
     * @param type
     * @param order
     * @param componentBuilder
     * @return
     * @param <C>
     */
    <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                        Integer order,
                                        @Nonnull ComponentDecorator<C> componentBuilder);

    <C> NewConfigurer registerCustomizer(@Nonnull Class<C> type,
                                         Integer order,
                                         @Nonnull UnaryOperator<Component<C>> componentCustomizer);

    /**
     * Registers an Axon module with this configuration. The module is initialized when the configuration is created and
     * has access to the global configuration when initialized.
     * <p>
     * Typically, modules are registered for Event Handling components or Sagas.
     *
     * @param module The module to register
     * @return the current instance of the Configurer, for chaining purposes
     */
    NewConfigurer registerModule(@Nonnull Module module);

    /**
     * Method returning a default component to use for given {@code type} for given {@code configuration}, or an empty
     * Optional if no default can be provided.
     *
     * @param type          The type of component to find a default for.
     * @param <C>           The type of component.
     * @return An Optional containing a default component, or empty if none can be provided.
     */
    <C> Optional<Component<C>> defaultComponent(@Nonnull Class<C> type);

    /**
     * Registers a command handler bean with this {@link NewConfigurer}. The bean may be of any type. The actual command
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link CommandHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link NewConfiguration} as input, and is expected to return a fully initialized
     * instance of the command handler bean.
     *
     * @param commandHandlerBuilder the builder function of the command handler bean
     * @return the current instance of the {@link NewConfigurer}, for chaining purposes
     */
    NewConfigurer registerCommandHandler(@Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder);

    /**
     * Registers a query handler bean with this {@link NewConfigurer}. The bean may be of any type. The actual query
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link QueryHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link NewConfiguration} as input, and is expected to return a fully initialized
     * instance of the query handler bean.
     *
     * @param queryHandlerBuilder the builder function of the query handler bean
     * @return the current instance of the {@link NewConfigurer}, for chaining purposes
     */
    NewConfigurer registerQueryHandler(@Nonnull ComponentBuilder<QueryHandler> queryHandlerBuilder);

    /**
     * Registers a message handler bean with this configuration. The bean may be of any type. The actual message handler
     * methods will be detected based on the annotations present on the bean's methods. Message handling functions
     * annotated with {@link CommandHandler}, {@link EventHandler} and {@link QueryHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link NewConfiguration} as input, and is expected to return a fully initialized
     * instance of the message handler bean.
     *
     * @param handlingComponentBuilder the builder function of the message handler bean
     * @return the current instance of the {@link NewConfigurer}, for chaining purposes
     */
    NewConfigurer registerMessageHandlingComponent(
            @Nonnull ComponentBuilder<MessageHandlingComponent> handlingComponentBuilder
    );

    /**
     * Configures the given Event Bus to use in this configuration. The builder receives the Configuration as input and
     * is expected to return a fully initialized {@link EventBus} instance.
     * <p>
     * Note that this builder should not be used when an Event Store is configured. Since Axon 3, the Event Store will
     * act as Event Bus implementation as well.
     *
     * @param eventBusBuilder The builder function for the {@link EventBus}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default NewConfigurer registerEventBus(@Nonnull ComponentBuilder<EventBus> eventBusBuilder) {
        return registerComponent(EventBus.class, eventBusBuilder);
    }

    /**
     * Configures the given Command Bus to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link CommandBus} instance.
     *
     * @param commandBusBuilder The builder function for the {@link CommandBus}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default NewConfigurer registerCommandBus(@Nonnull ComponentBuilder<CommandBus> commandBusBuilder) {
        return registerComponent(CommandBus.class, commandBusBuilder);
    }

    /**
     * Configures the given Query Bus to use in this configuration. The builder receives the Configuration as input and
     * is expected to return a fully initialized {@link QueryBus} instance.
     *
     * @param queryBusBuilder The builder function for the {@link QueryBus}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default NewConfigurer registerQueryBus(@Nonnull ComponentBuilder<QueryBus> queryBusBuilder) {
        return registerComponent(QueryBus.class, queryBusBuilder);
    }

    /**
     * Configures the given Query Update Emitter to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link QueryUpdateEmitter} instance.
     *
     * @param queryUpdateEmitterBuilder The builder function for the {@link QueryUpdateEmitter}
     * @return the current instance of the Configurer, for chaining purposes
     */
    default NewConfigurer registerQueryUpdateEmitter(
            @Nonnull ComponentBuilder<QueryUpdateEmitter> queryUpdateEmitterBuilder
    ) {
        return registerComponent(QueryUpdateEmitter.class, queryUpdateEmitterBuilder);
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
    default NewConfigurer configureLifecyclePhaseTimeout(long timeout, TimeUnit timeUnit) {
        return this;
    }

    /**
     * Register an initialization handler which should be invoked prior to starting this {@link NewConfigurer}.
     *
     * @param initHandler a {@link Consumer} of the configuration, to be run upon initialization of the
     *                    {@link NewConfiguration}
     */
    default void onInitialize(@Nonnull Consumer<NewConfiguration> initHandler) {
        registerModule(initHandler::accept);
    }

    /**
     * Returns the completely initialized Configuration built using this configurer. It is not recommended to change any
     * configuration on this Configurer once this method is called.
     *
     * @return the fully initialized Configuration
     */
    NewConfiguration buildConfiguration();

    /**
     * Builds the configuration and starts it immediately. It is not recommended to change any configuration on this
     * Configurer once this method is called.
     *
     * @return The started configuration
     */
    default NewConfiguration start() {
        NewConfiguration configuration = buildConfiguration();
        configuration.start();
        return configuration;
    }
}
