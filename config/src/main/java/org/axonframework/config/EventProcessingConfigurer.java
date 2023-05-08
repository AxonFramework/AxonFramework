/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.monitoring.MessageMonitor;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * Defines a contract for configuring event processing.
 *
 * @author Milan Savic
 * @since 4.0
 */
public interface EventProcessingConfigurer {

    /**
     * Registers a Saga with default configuration within this Configurer.
     *
     * @param sagaType the type of Saga
     * @param <T>      the type of Saga
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default <T> EventProcessingConfigurer registerSaga(Class<T> sagaType) {
        return registerSaga(sagaType, c -> {
        });
    }

    /**
     * Registers a Saga, allowing specific configuration to use for this Saga type.
     *
     * @param <T>            The type of Saga to configure
     * @param sagaType       The type of Saga to configure
     * @param sagaConfigurer a function providing modifications on top of the default configuration
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    <T> EventProcessingConfigurer registerSaga(Class<T> sagaType, Consumer<SagaConfigurer<T>> sagaConfigurer);

    /**
     * Registers a {@link Function} that builds a {@link SagaStore}.
     *
     * @param sagaStoreBuilder a {@link Function} that builds a {@link SagaStore}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerSagaStore(Function<Configuration, SagaStore> sagaStoreBuilder);

    /**
     * Registers a {@link Function} that builds an Event Handler instance.
     *
     * @param eventHandlerBuilder a {@link Function} that builds an Event Handler instance
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerEventHandler(Function<Configuration, Object> eventHandlerBuilder);

    /**
     * Registers a {@link Function} that builds the default {@link ListenerInvocationErrorHandler}.
     * Defaults to a {@link LoggingErrorHandler}.
     *
     * @param listenerInvocationErrorHandlerBuilder a {@link Function} that builds the default
     *                                              {@link ListenerInvocationErrorHandler}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultListenerInvocationErrorHandler(
            Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder);

    /**
     * Registers a {@link Function} that builds a {@link ListenerInvocationErrorHandler} for the given {@code
     * processingGroup}.
     *
     * @param processingGroup                       a {@link String} specifying the name of a processing group
     * @param listenerInvocationErrorHandlerBuilder a {@link Function} that builds {@link ListenerInvocationErrorHandler}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerListenerInvocationErrorHandler(String processingGroup,
                                                                     Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder);

    /**
     * Registers a {@link org.axonframework.eventhandling.TrackingEventProcessor} with given {@code name} within this
     * Configurer.
     *
     * @param name a {@link String} specifying the name of the {@link org.axonframework.eventhandling.TrackingEventProcessor}
     *             being registered
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer registerTrackingEventProcessor(String name) {
        return registerTrackingEventProcessor(name, c -> {
            EventBus eventBus = c.eventBus();
            if (!(eventBus instanceof StreamableMessageSource)) {
                throw new AxonConfigurationException(
                        "Cannot create Tracking Event Processor with name '" + name + "'. " +
                                "The available EventBus does not support tracking processors."
                );
            }
            //noinspection unchecked
            return (StreamableMessageSource) eventBus;
        });
    }

    /**
     * Configures which {@link StreamableMessageSource} to use for Tracking Event Processors if none was explicitly
     * provided. Defaults to the Event Bus (or Store) available in the Configuration.
     * <p>
     * Note that the configuration of a default source does <em>not</em> change how the decision is made to select the
     * type of processor. Unless explicitly specified using {@link #usingSubscribingEventProcessors()} or
     * {@link #usingTrackingEventProcessors()}, the default is dependent on the type of Message Source the Event Bus
     * provides. If the Event Bus supports Tracking Processors, that is the default, otherwise Subscribing Event
     * Processors are the default.
     *
     * @param defaultSource a Function that defines the Message source to use
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer configureDefaultStreamableMessageSource(Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> defaultSource);

    /**
     * Configures which {@link SubscribableMessageSource} to use for Subscribing Event Processors if none was explicitly
     * provided. Defaults to the Event Bus (or Store) available in the Configuration.
     * <p>
     * Note that the configuration of a default source does <em>not</em> change how the decision is made to select the
     * type of processor. Unless explicitly specified using {@link #usingSubscribingEventProcessors()} or
     * {@link #usingTrackingEventProcessors()}, the default is dependent on the type of Message Source the Event Bus
     * provides. If the Event Bus supports Tracking Processors, that is the default, otherwise Subscribing Event
     * Processors are the default.
     *
     * @param defaultSource a Function that defines the Message source to use
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer configureDefaultSubscribableMessageSource(Function<Configuration, SubscribableMessageSource<EventMessage<?>>> defaultSource);

    /**
     * Registers a {@link org.axonframework.eventhandling.TrackingEventProcessor} with given {@code name} and {@code
     * source} within this Configurer.
     *
     * @param name   a {@link String} specifying the name of the {@link org.axonframework.eventhandling.TrackingEventProcessor}
     *               being registered
     * @param source a {@link Function} that builds a {@link StreamableMessageSource}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerTrackingEventProcessor(String name,
                                                             Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source);

    /**
     * Registers a {@link org.axonframework.eventhandling.TrackingEventProcessor} with given {@code name}, {@code
     * source} and {@code processorConfiguration} within this Configurer.
     *
     * @param name                   a {@link String} specifying the name of the {@link org.axonframework.eventhandling.TrackingEventProcessor}
     *                               being registered
     * @param source                 a {@link Function} that builds {@link StreamableMessageSource}
     * @param processorConfiguration a {@link Function} that builds a {@link TrackingEventProcessorConfiguration}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerTrackingEventProcessor(String name,
                                                             Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                             Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration);

    /**
     * Registers a factory that builds the default {@link EventProcessor}. This is the {@link EventProcessorBuilder} to
     * be used when there is no specific builder for given processor name.
     *
     * @param eventProcessorBuilder a {@link Function} that builds an {@link EventProcessor}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder);

    /**
     * Registers an {@link EventProcessorBuilder} for the given processor {@code name}.
     *
     * @param name                  a {@link String} specifying the name of the {@link EventProcessor} being registered
     * @param eventProcessorBuilder a {@link Function} that builds an {@link EventProcessor}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerEventProcessor(String name, EventProcessorBuilder eventProcessorBuilder);

    /**
     * Register a {@link Function} that builds a {@link TokenStore} for the given {@code processorName}.
     *
     * @param processorName     a {@link String} specifying the name of a event processor
     * @param tokenStoreBuilder a {@link Function} that builds a {@link TokenStore}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerTokenStore(String processorName,
                                                 Function<Configuration, TokenStore> tokenStoreBuilder);

    /**
     * Register a {@link Function} that builds a {@link TokenStore} to use as the default in case no explicit token
     * store was configured for a processor.
     *
     * @param tokenStore a {@link Function} that builds a {@link TokenStore}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerTokenStore(Function<Configuration, TokenStore> tokenStore);

    /**
     * Defaults Event Processors builders to use {@link org.axonframework.eventhandling.SubscribingEventProcessor}.
     * <p>
     * The default behavior depends on the EventBus available in the Configuration. If the Event Bus is a
     * {@link StreamableMessageSource}, processors are Tracking by default. This method must be used to force the use
     * of Subscribing Processors, unless specifically overridden for individual processors.
     *
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer usingSubscribingEventProcessors();

    /**
     * Defaults Event Processors builders to use {@link org.axonframework.eventhandling.TrackingEventProcessor}.
     * <p>
     * The default behavior depends on the EventBus available in the Configuration. If the Event Bus is a
     * {@link StreamableMessageSource}, processors are Tracking by default. This method must be used to force the use
     * of Tracking Processors, unless specifically overridden for individual processors.
     *
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer usingTrackingEventProcessors();

    /**
     * Defaults Event Processors builders to use {@link PooledStreamingEventProcessor}.
     * <p>
     * The default behavior depends on the {@link EventBus} available in the {@link Configuration}. If the {@code
     * EventBus} is a {@link StreamableMessageSource}, processors are Tracking by default. This method must be used to
     * force the use of Pooled Streaming Processors, unless specifically overridden for individual processors.
     *
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer usingPooledStreamingEventProcessors();

    /**
     * Defaults Event Processors builders to construct a {@link PooledStreamingEventProcessor} using the
     * {@code configuration} to configure them.
     * <p>
     * The default behavior depends on the {@link EventBus} available in the {@link Configuration}. If the
     * {@code EventBus} is a {@link StreamableMessageSource}, processors are Tracking by default. This method must be
     * used to force the use of Pooled Streaming Processors, unless specifically overridden for individual processors.
     *
     * @param pooledStreamingProcessorConfiguration configuration used when constructing every
     *                                              {@link PooledStreamingEventProcessor}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer usingPooledStreamingEventProcessors(
            PooledStreamingProcessorConfiguration pooledStreamingProcessorConfiguration
    ) {
        return usingPooledStreamingEventProcessors()
                .registerPooledStreamingEventProcessorConfiguration(pooledStreamingProcessorConfiguration);
    }

    /**
     * Registers a {@link org.axonframework.eventhandling.SubscribingEventProcessor} with given {@code name} within this
     * Configurer.
     *
     * @param name a {@link String} specyfing the name of the {@link org.axonframework.eventhandling.SubscribingEventProcessor}
     *             being registered
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer registerSubscribingEventProcessor(String name) {
        return registerSubscribingEventProcessor(name, Configuration::eventBus);
    }

    /**
     * Registers a {@link org.axonframework.eventhandling.SubscribingEventProcessor} with given {@code name} and {@code
     * messageSource} within this Configuration.
     *
     * @param name          a {@link String} specyfing the name of the {@link org.axonframework.eventhandling.SubscribingEventProcessor}
     *                      being registered
     * @param messageSource a {@link Function} that builds a {@link SubscribableMessageSource}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerSubscribingEventProcessor(String name,
                                                                Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource);

    /**
     * Registers a {@link Function} that builds the default {@link ErrorHandler}. Defaults to a
     * {@link org.axonframework.eventhandling.PropagatingErrorHandler}.
     *
     * @param errorHandlerBuilder a {@link Function} that builds an {@link ErrorHandler}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultErrorHandler(Function<Configuration, ErrorHandler> errorHandlerBuilder);

    /**
     * Registers a {@link Function} that builds an {@link ErrorHandler} for the given {@code eventProcessorName}.
     *
     * @param eventProcessorName  a {@link String} specifying the name of an {@link EventProcessor}
     * @param errorHandlerBuilder a {@link Function} that builds an {@link ErrorHandler}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerErrorHandler(String eventProcessorName,
                                                   Function<Configuration, ErrorHandler> errorHandlerBuilder);

    /**
     * Registers the {@code processingGroup} name to assign Event Handler and Saga beans to when no other, more
     * explicit, rule matches and no {@link ProcessingGroup} annotation is found.
     *
     * @param processingGroup a {@link String} specifying the name of a processing group
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer byDefaultAssignTo(String processingGroup) {
        byDefaultAssignHandlerTypesTo(c -> processingGroup);
        return byDefaultAssignHandlerInstancesTo(o -> processingGroup);
    }

    /**
     * Registers a {@link Function} that defines the Event Processing Group name to assign Event Handler beans to when
     * no other, more explicit, rule matches and no {@link ProcessingGroup} annotation is found.
     *
     * @param assignmentFunction a {@link Function} that returns the Processing Group for each Event Handler bean
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer byDefaultAssignHandlerInstancesTo(Function<Object, String> assignmentFunction);

    /**
     * Registers a {@link Function} that defines the Event Processing Group name to assign Event Handler and Saga beans
     * to when no other, more explicit, rule matches and no {@link ProcessingGroup} annotation is found.
     *
     * @param assignmentFunction a {@link Function} that returns the Processing Group for each Event Handler or Saga
     *                           bean
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer byDefaultAssignHandlerTypesTo(Function<Class<?>, String> assignmentFunction);

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Processing Group
     * with given {@code name}, with neutral priority (value 0).
     * <p>
     * Note that, when beans match multiple criteria for different Processing Groups with equal priority, the outcome is
     * undefined.
     *
     * @param processingGroup a {@link String} specifying the name of a processing group to assign matching Event
     *                        Handlers to
     * @param criteria        a {@link Predicate} defining the criteria for an Event Handler to match
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer assignHandlerInstancesMatching(String processingGroup,
                                                                     Predicate<Object> criteria) {
        return assignHandlerInstancesMatching(processingGroup, 0, criteria);
    }

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Processing Group
     * with given {@code name}, with neutral priority (value 0).
     * <p>
     * Note that, when beans match multiple criteria for different Processing Groups with equal priority, the outcome is
     * undefined.
     *
     * @param processingGroup a {@link String} specifying the name of a processing group to assign matching Event
     *                        Handlers or Sagas to
     * @param criteria        a {@link Predicate} defining the criteria for an Event Handler or Saga to match
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer assignHandlerTypesMatching(String processingGroup, Predicate<Class<?>> criteria) {
        return assignHandlerTypesMatching(processingGroup, 0, criteria);
    }

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Processing Group
     * with given {@code name}, with given {@code priority}. Rules with higher value of {@code priority} take precedence
     * over those with a lower value.
     * <p>
     * Note that, when beans match multiple criteria for different processing groups with equal priority, the outcome is
     * undefined.
     *
     * @param processingGroup a {@link String} specifying the name of a processing group to assign matching Event
     *                        Handlers to
     * @param priority        The priority for this rule
     * @param criteria        a {@link Predicate} defining the criteria for an Event Handler to match
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer assignHandlerInstancesMatching(String processingGroup,
                                                             int priority,
                                                             Predicate<Object> criteria);

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Processing Group
     * with given {@code name}, with given {@code priority}. Rules with higher value of {@code priority} take precedence
     * over those with a lower value.
     * <p>
     * Note that, when beans match multiple criteria for different processing groups with equal priority, the outcome is
     * undefined.
     *
     * @param processingGroup a {@link String} specifying the name of the Processing Group to assign matching Event
     *                        Handlers or Sagas to
     * @param priority        an {@code int} specifying the priority of this rule
     * @param criteria        a {@link Predicate} defining the criteria for an Event Handler or Saga to match
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer assignHandlerTypesMatching(String processingGroup,
                                                         int priority,
                                                         Predicate<Class<?>> criteria);

    /**
     * Defines a mapping for assigning processing groups to processors.
     *
     * @param processingGroup a {@link String} specifying the processing group to be assigned
     * @param processorName   a {@link String} specifying the processor name to assign the group to
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer assignProcessingGroup(String processingGroup, String processorName);

    /**
     * Defines a rule for assigning processing groups to processors if processing group to processor name mapping does
     * not contain the entry.
     *
     * @param assignmentRule a {@link Function} which takes a processing group and returns a processor name
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     * @see #assignProcessingGroup(String, String)
     */
    EventProcessingConfigurer assignProcessingGroup(Function<String, String> assignmentRule);

    /**
     * Register the given {@code interceptorBuilder} to build a {@link MessageHandlerInterceptor} for the
     * {@link EventProcessor} with given {@code processorName}.
     * <p>
     * The {@code interceptorBuilder} may return {@code null}, in which case the return value is ignored.
     *
     * @param processorName      a {@link String} specyfing the name of the processor to register the
     *                           {@link MessageHandlerInterceptor} on
     * @param interceptorBuilder a {@link Function} providing the {@link MessageHandlerInterceptor} to register, or
     *                           {@code null}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerHandlerInterceptor(String processorName,
                                                         Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder);

    /**
     * Register the given {@code interceptorBuilder} as a default to build a {@link MessageHandlerInterceptor} for
     * {@link EventProcessor}s created in this configuration.
     * <p>
     * The {@code interceptorBuilder} is invoked once for each processor created, and may return {@code null}, in which
     * case the return value is ignored.
     *
     * @param interceptorBuilder a builder {@link Function} that provides a {@link MessageHandlerInterceptor} for each
     *                           available processor
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultHandlerInterceptor(
            BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder);

    /**
     * Registers the {@link SequencingPolicy} created by the given {@code policyBuilder} to the processing group with
     * given {@code processingGroup}. Any previously configured policy for the same name will be overwritten.
     *
     * @param processingGroup a {@link String} specifying the name of the processing group to assign the
     *                        {@link SequencingPolicy} for
     * @param policyBuilder   a builder {@link Function} to create the {@link SequencingPolicy} to use
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerSequencingPolicy(String processingGroup,
                                                       Function<Configuration, SequencingPolicy<? super EventMessage<?>>> policyBuilder);

    /**
     * Registers the {@link SequencingPolicy} created by given {@code policyBuilder} to the processing groups for which
     * no explicit policy is defined (using {@link #registerSequencingPolicy(String, Function)}).
     * <p>
     * Defaults to a {@link SequentialPerAggregatePolicy}.
     *
     * @param policyBuilder a builder {@link Function} to create the {@link SequencingPolicy} to use
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultSequencingPolicy(
            Function<Configuration, SequencingPolicy<? super EventMessage<?>>> policyBuilder);

    /**
     * Registers a builder {@link Function} to create the {@link MessageMonitor} for a {@link EventProcessor} of the
     * given {@code name}.
     *
     * @param eventProcessorName    a {@link String} specifying the name of an {@link EventProcessor}
     * @param messageMonitorBuilder a builder {@link Function} to create a {@link MessageMonitor}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer registerMessageMonitor(String eventProcessorName,
                                                             Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
        return registerMessageMonitorFactory(
                eventProcessorName,
                (configuration, componentType, componentName) -> messageMonitorBuilder.apply(configuration)
        );
    }

    /**
     * Registers the factory to create the {@link MessageMonitor} for a {@link EventProcessor} of the given
     * {@code name}.
     *
     * @param eventProcessorName    a {@link String} specifying the name of an {@link EventProcessor}
     * @param messageMonitorFactory a {@link MessageMonitorFactory} used to create a {@link MessageMonitor}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerMessageMonitorFactory(String eventProcessorName,
                                                            MessageMonitorFactory messageMonitorFactory);

    /**
     * Registers a {@link Function} that builds the {@link RollbackConfiguration} for given processor {@code name}.
     * Defaults to a {@link org.axonframework.messaging.unitofwork.RollbackConfigurationType#ANY_THROWABLE}
     *
     * @param name                         a {@link String} specifying the name of an {@link EventProcessor}
     * @param rollbackConfigurationBuilder a {@link Function} that builds a {@link RollbackConfiguration}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerRollbackConfiguration(String name,
                                                            Function<Configuration, RollbackConfiguration> rollbackConfigurationBuilder);

    /**
     * Registers a {@link TransactionManager} for a {@link EventProcessor} of the given {@code name}.
     *
     * @param name                      a {@link String} specifying the name of an {@link EventProcessor}
     * @param transactionManagerBuilder a {@link Function} that builds a {@link TransactionManager}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerTransactionManager(String name,
                                                         Function<Configuration, TransactionManager> transactionManagerBuilder);

    /**
     * Registers a default {@link TransactionManager} for all {@link EventProcessor}s. The provided {@code
     * TransactionManager} is used whenever no processor specific {@code TransactionManager} is configured.
     *
     * @param transactionManagerBuilder a {@link Function} that builds a {@link TransactionManager}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultTransactionManager(
            Function<Configuration, TransactionManager> transactionManagerBuilder
    );

    /**
     * Register a {@link Function} that builds a {@link TrackingEventProcessorConfiguration} to be used by the {@link
     * EventProcessor} corresponding to the given {@code name}.
     *
     * @param name                                       a {@link String} specifying the name of an {@link
     *                                                   EventProcessor}
     * @param trackingEventProcessorConfigurationBuilder a {@link Function} that builds a {@link TrackingEventProcessorConfiguration}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerTrackingEventProcessorConfiguration(
            String name,
            Function<Configuration, TrackingEventProcessorConfiguration> trackingEventProcessorConfigurationBuilder
    );

    /**
     * Register a {@link Function} that builds a {@link TrackingEventProcessorConfiguration} to use as the default.
     *
     * @param trackingEventProcessorConfigurationBuilder a {@link Function} that builds a {@link TrackingEventProcessorConfiguration}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerTrackingEventProcessorConfiguration(
            Function<Configuration, TrackingEventProcessorConfiguration> trackingEventProcessorConfigurationBuilder
    );

    /**
     * Registers a {@link PooledStreamingEventProcessor} in this {@link EventProcessingConfigurer}. The processor will
     * receive the given {@code name}.
     *
     * @param name the name of the {@link PooledStreamingEventProcessor} being registered
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer registerPooledStreamingEventProcessor(String name) {
        return registerPooledStreamingEventProcessor(name, c -> {
            EventBus eventBus = c.eventBus();
            if (!(eventBus instanceof StreamableMessageSource)) {
                throw new AxonConfigurationException(
                        "Cannot create Pooled Streaming Event Processor with name '" + name + "'. " +
                                "The available EventBus does not support tracking processors."
                );
            }
            //noinspection unchecked
            return (StreamableMessageSource<TrackedEventMessage<?>>) eventBus;
        });
    }

    /**
     * Registers a {@link PooledStreamingEventProcessor} in this {@link EventProcessingConfigurer}. The processor will
     * receive the given {@code name} and use the outcome of the {@code messageSource} as the {@link
     * StreamableMessageSource}.
     *
     * @param name          the name of the {@link PooledStreamingEventProcessor} being registered
     * @param messageSource constructs a {@link StreamableMessageSource} to be used by the {@link
     *                      PooledStreamingEventProcessor}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer registerPooledStreamingEventProcessor(
            String name,
            Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource
    ) {
        return registerPooledStreamingEventProcessor(name, messageSource, PooledStreamingProcessorConfiguration.noOp());
    }

    /**
     * Registers a {@link PooledStreamingEventProcessor} in this {@link EventProcessingConfigurer}. The processor will
     * receive the given {@code name}  and use the outcome of the {@code messageSource} as the {@link
     * StreamableMessageSource}.
     * <p>
     * The {@code processorConfiguration} will be used to further configure the {@code PooledStreamingEventProcessor}
     * upon construction. Note that the {@code processorConfiguration} will override any configuration set through the
     * {@link #registerPooledStreamingEventProcessorConfiguration(PooledStreamingProcessorConfiguration)} and {@link
     * #registerPooledStreamingEventProcessorConfiguration(String, PooledStreamingProcessorConfiguration)}.
     *
     * @param name                   the name of the {@link PooledStreamingEventProcessor} being registered
     * @param messageSource          constructs a {@link StreamableMessageSource} to be used by the {@link
     *                               PooledStreamingEventProcessor}
     * @param processorConfiguration allows further customization of the {@link PooledStreamingEventProcessor} under
     *                               construction. The given {@link Configuration} can be used to extract components and
     *                               use them in the {@link PooledStreamingEventProcessor.Builder}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerPooledStreamingEventProcessor(
            String name,
            Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource,
            PooledStreamingProcessorConfiguration processorConfiguration
    );

    /**
     * Register a default {@link PooledStreamingProcessorConfiguration} to be used when constructing every {@link
     * PooledStreamingEventProcessor}.
     *
     * @param pooledStreamingProcessorConfiguration configuration used when constructing every {@link
     *                                              PooledStreamingEventProcessor}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerPooledStreamingEventProcessorConfiguration(
            PooledStreamingProcessorConfiguration pooledStreamingProcessorConfiguration
    );

    /**
     * Register a {@link PooledStreamingProcessorConfiguration} to be used when constructing a {@link
     * PooledStreamingEventProcessor} with {@code name}.
     *
     * @param name                                  the name of an {@link PooledStreamingEventProcessor}
     * @param pooledStreamingProcessorConfiguration configuration used when constructing a {@link PooledStreamingEventProcessor}
     *                                              with the given {@code name}
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    EventProcessingConfigurer registerPooledStreamingEventProcessorConfiguration(
            String name, PooledStreamingProcessorConfiguration pooledStreamingProcessorConfiguration
    );

    /**
     * Register a {@link SequencedDeadLetterQueue} for the given {@code processingGroup}. The
     * {@code SequencedDeadLetterQueue} will automatically enqueue failed events and evaluate them per the queue's
     * configuration.
     *
     * @param processingGroup A {@link String} specifying the name of the processing group to register the given
     *                        {@link SequencedDeadLetterQueue} for.
     * @param queueBuilder    A builder method returning a {@link SequencedDeadLetterQueue} based on a
     *                        {@link Configuration}. The outcome is used by the given {@code processingGroup} to enqueue
     *                        and evaluate failed events in.
     * @return The current {@link EventProcessingConfigurer} instance, for fluent interfacing.
     */
    default EventProcessingConfigurer registerDeadLetterQueue(
            @Nonnull String processingGroup,
            @Nonnull Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>> queueBuilder
    ) {
        return this;
    }

    /**
     * Register a default {@link EnqueuePolicy dead letter policy} for any processing group using a
     * {@link #registerDeadLetterQueue(String, Function) dead letter queue}. The processing group uses the policy to
     * deduce whether a failed {@link EventMessage} should be
     * {@link SequencedDeadLetterQueue#enqueue(Object, DeadLetter) enqueued} for later evaluation.
     * <p>
     * Note that the configured component will not be used if the processing group <em>does not</em> have a dead letter
     * queue.
     *
     * @param policyBuilder A builder method to construct a default {@link EnqueuePolicy dead letter policy}.
     * @return The current {@link EventProcessingConfigurer} instance, for fluent interfacing.
     */
    default EventProcessingConfigurer registerDefaultDeadLetterPolicy(
            @Nonnull Function<Configuration, EnqueuePolicy<EventMessage<?>>> policyBuilder
    ) {
        return this;
    }

    /**
     * Register a {@link EnqueuePolicy dead letter policy} for the given {@code processingGroup} using a
     * {@link #registerDeadLetterQueue(String, Function) dead letter queue}. The processing group uses the policy to
     * deduce whether a failed {@link EventMessage} should be
     * {@link SequencedDeadLetterQueue#enqueue(Object, DeadLetter) enqueued} for later evaluation.
     * <p>
     * Note that the configured component will not be used if the processing group <em>does not</em> have a dead letter
     * queue.
     *
     * @param processingGroup The name of the processing group to build an {@link EnqueuePolicy} for.
     * @param policyBuilder   A builder method to construct a {@link EnqueuePolicy dead letter policy} for the given
     *                        {@code processingGroup}.
     * @return The current {@link EventProcessingConfigurer} instance, for fluent interfacing.
     */
    default EventProcessingConfigurer registerDeadLetterPolicy(
            @Nonnull String processingGroup,
            @Nonnull Function<Configuration, EnqueuePolicy<EventMessage<?>>> policyBuilder
    ) {
        return this;
    }

    /**
     * Register a {@link DeadLetteringInvokerConfiguration} for the given {@code processingGroup}. This configuration
     * object allows for fine-grained customization of a
     * {@link DeadLetteringEventHandlerInvoker dead lettering processing group} through its
     * {@link DeadLetteringEventHandlerInvoker.Builder builder}.
     * <p>
     * Note that the configured component will not be used if the processing group <em>does not</em> have a dead letter
     * queue.
     *
     * @param processingGroup The name of the processing group to attach additional configuration too.
     * @param configuration   The additional configuration for the dead lettering processing group.
     * @return The current {@link EventProcessingConfigurer} instance, for fluent interfacing.
     */
    default EventProcessingConfigurer registerDeadLetteringEventHandlerInvokerConfiguration(
            @Nonnull String processingGroup,
            @Nonnull DeadLetteringInvokerConfiguration configuration
    ) {
        return this;
    }

    /**
     * Contract which defines how to build an event processor.
     */
    @FunctionalInterface
    interface EventProcessorBuilder {

        /**
         * Builds an {@link EventProcessor} with the given {@code name}, {@link Configuration} and
         * {@link EventHandlerInvoker}.
         *
         * @param name                a {@link String} specifying the name of the {@link EventProcessor} to create
         * @param configuration       the global {@link Configuration} the implementation may use to obtain dependencies
         * @param eventHandlerInvoker the {@link EventHandlerInvoker} assigned to the {@link EventProcessor} to be
         *                            created, used to invoke event handlers
         * @return an {@link EventProcessor}
         */
        EventProcessor build(String name, Configuration configuration, EventHandlerInvoker eventHandlerInvoker);
    }

    /**
     * Contract defining {@link PooledStreamingEventProcessor.Builder} based configuration when constructing a {@link
     * PooledStreamingEventProcessor}.
     */
    @FunctionalInterface
    interface PooledStreamingProcessorConfiguration extends
            BiFunction<Configuration, PooledStreamingEventProcessor.Builder, PooledStreamingEventProcessor.Builder> {

        /**
         * Returns a configuration that applies the given {@code other} configuration after applying {@code this}. Any
         * configuration set by the {@code other} will override changes by {@code this} instance.
         *
         * @param other The configuration to apply after applying this
         * @return a configuration that applies both this and then the other configuration
         */
        default PooledStreamingProcessorConfiguration andThen(PooledStreamingProcessorConfiguration other) {
            return (config, builder) -> other.apply(config, this.apply(config, builder));
        }

        /**
         * A {@link PooledStreamingProcessorConfiguration} which does not add any configuration.
         *
         * @return a {@link PooledStreamingProcessorConfiguration} which does not add any configuration
         */
        static PooledStreamingProcessorConfiguration noOp() {
            return (config, builder) -> builder;
        }
    }

    /**
     * Contract defining {@link DeadLetteringEventHandlerInvoker.Builder} based configuration when constructing a
     * {@link DeadLetteringEventHandlerInvoker}.
     */
    @FunctionalInterface
    interface DeadLetteringInvokerConfiguration extends
            BiFunction<Configuration, DeadLetteringEventHandlerInvoker.Builder, DeadLetteringEventHandlerInvoker.Builder> {

        /**
         * Returns a configuration that applies the given {@code other} configuration after applying {@code this}. Any
         * configuration set by the {@code other} will override changes by {@code this} instance.
         *
         * @param other The configuration to apply after applying this.
         * @return A configuration that applies both this and then the other configuration.
         */
        default DeadLetteringInvokerConfiguration andThen(DeadLetteringInvokerConfiguration other) {
            return (config, builder) -> other.apply(config, this.apply(config, builder));
        }

        /**
         * A {@link DeadLetteringInvokerConfiguration} which does not add any configuration.
         *
         * @return A {@link DeadLetteringInvokerConfiguration} which does not add any configuration.
         */
        static DeadLetteringInvokerConfiguration noOp() {
            return (config, builder) -> builder;
        }
    }

    /**
     * Register the given {@code deadLetterProvider} as a default to build a {@link SequencedDeadLetterQueue} for
     * {@link EventProcessor}s created in this configuration.
     * <p>
     * The {@code deadLetterProvider} might return null if the given processing group name should not have a sequenced
     * dead letter queue. An explicit set sequenced dead letter queue set using
     * {@link #registerDeadLetterQueue(String, Function)} will always have precedence over the one provided by this
     * provider.
     *
     * @param deadLetterQueueProvider a builder {@link Function} that provides a {@link SequencedDeadLetterQueue} for a
     *                                processing group. It's possible to return null depending on the processing group.
     * @return the current {@link EventProcessingConfigurer} instance, for fluent interfacing
     */
    default EventProcessingConfigurer registerDeadLetterQueueProvider(
            Function<String, Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueueProvider) {
        return this;
    }
}
