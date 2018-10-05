/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.monitoring.MessageMonitor;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Defines a contract for configuring event processing.
 *
 * @author Milan Savic
 * @since 4.0
 */
public interface EventProcessingConfigurer {

    /**
     * Contract which defines how to build an event processor.
     */
    @FunctionalInterface interface EventProcessorBuilder {

        /**
         * Builds the event processor.
         *
         * @param name                The name of the Event Processor to create
         * @param configuration       The global configuration the implementation may use to obtain dependencies
         * @param eventHandlerInvoker The invoker which is used to invoke event handlers and assigned to this processor
         * @return the event processor
         */
        EventProcessor build(String name, Configuration configuration, EventHandlerInvoker eventHandlerInvoker);
    }

    /**
     * Registers a function that builds Saga Configuration.
     *
     * @param sagaConfigurationBuilder A function that builds Saga Configuration
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerSagaConfiguration(
            Function<Configuration, SagaConfiguration<?>> sagaConfigurationBuilder);

    /**
     * Registers a function that builds Saga Store
     *
     * @param sagaStoreBuilder A function that builds Saga Store
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerSagaStore(
            Function<Configuration, SagaStore> sagaStoreBuilder);

    /**
     * Registers a function that builds an Event Handler instance.
     *
     * @param eventHandlerBuilder A function that builds an Event Handler instance
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerEventHandler(
            Function<Configuration, Object> eventHandlerBuilder);

    /**
     * Registers a function that builds the default Listener Invocation Error Handler.
     *
     * @param listenerInvocationErrorHandlerBuilder A function that builds the default Listener Invocation Error Handler
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultListenerInvocationErrorHandler(
            Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder);

    /**
     * Registers a function that builds the Listener Invocation Error Handler for given {@code processingGroup}.
     *
     * @param processingGroup                       The name of processing group
     * @param listenerInvocationErrorHandlerBuilder A function that builds Listener Invocation Error Handler
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerListenerInvocationErrorHandler(String processingGroup,
                                                                     Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder);

    /**
     * Registers a {@link org.axonframework.eventhandling.TrackingEventProcessor} with given {@code name} within this
     * Configurer.
     *
     * @param name The name of {@link org.axonframework.eventhandling.TrackingEventProcessor}
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    default EventProcessingConfigurer registerTrackingEventProcessor(String name) {
        return registerTrackingEventProcessor(name, Configuration::eventBus);
    }

    /**
     * Registers a {@link org.axonframework.eventhandling.TrackingEventProcessor} with given {@code name} and {@code
     * source} within this Configurer.
     *
     * @param name   The name of {@link org.axonframework.eventhandling.TrackingEventProcessor}
     * @param source A function that builds Streamable Message Source
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    default EventProcessingConfigurer registerTrackingEventProcessor(String name,
                                                                     Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return registerTrackingEventProcessor(name,
                                              source,
                                              c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                                                  TrackingEventProcessorConfiguration::forSingleThreadedProcessing));
    }

    /**
     * Registers a {@link org.axonframework.eventhandling.TrackingEventProcessor} with given {@code name}, {@code
     * source} and {@code processorConfiguration} within this Configurer.
     *
     * @param name                   The name of {@link org.axonframework.eventhandling.TrackingEventProcessor}
     * @param source                 A function that builds Streamable Message Source
     * @param processorConfiguration A function that builds Tracking Event Processor Configuration
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerTrackingEventProcessor(String name,
                                                             Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                             Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration);

    /**
     * Registers a factory that builds the default Event Processor - Event Processor Builder to be used when there is no
     * specific builder for given processor name.
     *
     * @param eventProcessorBuilder A function that builds Event Processor
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder);

    /**
     * Registers a Event Processor Builder for given processor {@code name}.
     *
     * @param name                  The name of the processor
     * @param eventProcessorBuilder A function that builds Event Processor
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerEventProcessor(String name,
                                                     EventProcessorBuilder eventProcessorBuilder);

    /**
     * Register a function that builds a Token Store for given {@code processingGroup}.
     *
     * @param processingGroup   The name of processing group
     * @param tokenStoreBuilder A function that builds a Token Store
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerTokenStore(String processingGroup,
                                                 Function<Configuration, TokenStore> tokenStoreBuilder);

    /**
     * Defaults Event Processors builders to use {@link org.axonframework.eventhandling.SubscribingEventProcessor}.
     *
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer usingSubscribingEventProcessors();

    /**
     * Registers a {@link org.axonframework.eventhandling.SubscribingEventProcessor} with given {@code name} within this
     * Configurer.
     *
     * @param name The name of {@link org.axonframework.eventhandling.SubscribingEventProcessor}
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    default EventProcessingConfigurer registerSubscribingEventProcessor(String name) {
        return registerSubscribingEventProcessor(name, Configuration::eventBus);
    }

    /**
     * Registers a {@link org.axonframework.eventhandling.SubscribingEventProcessor} with given {@code name} and {@code
     * messageSource} within this Configuration.
     *
     * @param name          The name of {@link org.axonframework.eventhandling.SubscribingEventProcessor}
     * @param messageSource The function that builds Subscribable Message Source
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerSubscribingEventProcessor(String name,
                                                                Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource);

    /**
     * Registers a function to be used as default Error Handler builder.
     *
     * @param errorHandlerBuilder A function that builds an Error Handler
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultErrorHandler(
            Function<Configuration, ErrorHandler> errorHandlerBuilder);

    /**
     * Registers a function to be used as Error Handler for given {@code eventProcessorName}.
     *
     * @param eventProcessorName  The name of Event Processor
     * @param errorHandlerBuilder A function that builds Error Handler
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerErrorHandler(String eventProcessorName,
                                                   Function<Configuration, ErrorHandler> errorHandlerBuilder);

    /**
     * Registers the Processing Group name to assign Event Handler and Saga beans to when no other, more explicit, rule
     * matches and no {@link ProcessingGroup} annotation is found.
     *
     * @param processingGroup The name of processing group
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    default EventProcessingConfigurer byDefaultAssignTo(String processingGroup) {
        byDefaultAssignHandlerTypesTo(c -> processingGroup);
        return byDefaultAssignHandlerInstancesTo(o -> processingGroup);
    }

    /**
     * Registers a function that defines the Event Processing Group name to assign Event Handler beans to when no other,
     * more explicit, rule matches and no {@link ProcessingGroup} annotation is found.
     *
     * @param assignmentFunction The function that returns the Processing Group for each Event Handler bean
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer byDefaultAssignHandlerInstancesTo(Function<Object, String> assignmentFunction);

    /**
     * Registers a function that defines the Event Processing Group name to assign Event Handler and Saga beans to when
     * no other, more explicit, rule matches and no {@link ProcessingGroup} annotation is found.
     *
     * @param assignmentFunction The function that returns the Processing Group for each Event Handler or Saga bean
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer byDefaultAssignHandlerTypesTo(Function<Class<?>, String> assignmentFunction);

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Processing Group
     * with given {@code name}, with neutral priority (value 0).
     * <p>
     * Note that, when beans match multiple criteria for different Processing Groups with equal priority, the outcome is
     * undefined.
     *
     * @param processingGroup The name of the Processing Group to assign matching Event Handlers to
     * @param criteria        The criteria for Event Handler to match
     * @return an instance of Event Processing Configurer for fluent interfacing
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
     * @param processingGroup The name of the Processing Group to assign matching Event Handlers or Sagas to
     * @param criteria        The criteria for Event Handler or Saga to match
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    default EventProcessingConfigurer assignHandlerTypesMatching(String processingGroup,
                                                                 Predicate<Class<?>> criteria) {
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
     * @param processingGroup The name of the Processing Group to assign matching Event Handlers to
     * @param priority        The priority for this rule
     * @param criteria        The criteria for Event Handler to match
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer assignHandlerInstancesMatching(String processingGroup, int priority,
                                                             Predicate<Object> criteria);

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Processing Group
     * with given {@code name}, with given {@code priority}. Rules with higher value of {@code priority} take precedence
     * over those with a lower value.
     * <p>
     * Note that, when beans match multiple criteria for different processing groups with equal priority, the outcome is
     * undefined.
     *
     * @param processingGroup The name of the Processing Group to assign matching Event Handlers or Sagas to
     * @param priority        The priority for this rule
     * @param criteria        The criteria for Event Handler or Saga to match
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer assignHandlerTypesMatching(String processingGroup, int priority,
                                                         Predicate<Class<?>> criteria);

    /**
     * Defines a mapping for assigning processing groups to processors.
     *
     * @param processingGroup The processing group to be assigned
     * @param processorName   The processor name to assign group to
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer assignProcessingGroup(String processingGroup, String processorName);

    /**
     * Defines a rule for assigning processing groups to processors if processing group to processor name mapping does
     * not contain the entry.
     *
     * @param assignmentRule The function which takes processing group and returns processor name
     * @return an instance of Event Processing Configurer for fluent interfacing
     *
     * @see #assignProcessingGroup(String, String)
     */
    EventProcessingConfigurer assignProcessingGroup(Function<String, String> assignmentRule);

    /**
     * Register the given {@code interceptorBuilder} to build an Message Handling Interceptor for the Event Processor
     * with given {@code processorName}.
     * <p>
     * The {@code interceptorBuilder} may return {@code null}, in which case the return value is ignored.
     *
     * @param processorName      The name of the processor to register the interceptor on
     * @param interceptorBuilder The function providing the interceptor to register, or {@code null}
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerHandlerInterceptor(String processorName,
                                                         Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder);

    /**
     * Register the given {@code interceptorBuilder} as a default to build an Message Handling Interceptor for Event
     * Processors created in this configuration.
     * <p>
     * The {@code interceptorBuilder} is invoked once for each processor created, and may return {@code null}, in which
     * case the return value is ignored.
     * <p>
     *
     * @param interceptorBuilder The builder function that provides an interceptor for each available processor
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultHandlerInterceptor(
            BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder);

    /**
     * Registers the sequencing policy created by given {@code policyBuilder} to the processing group with given
     * {@code processingGroup}. Any previously configured policy for the same name will be overwritten.
     *
     * @param processingGroup The name of the processing group to assign the sequencing policy for
     * @param policyBuilder   The builder function to create the policy to use
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerSequencingPolicy(String processingGroup,
                                                       Function<Configuration, SequencingPolicy<? super EventMessage<?>>> policyBuilder);

    /**
     * Registers the sequencing policy created by given {@code policyBuilder} to the processing groups for which no
     * explicit policy is defined (using {@link #registerSequencingPolicy(String, Function)}).
     * <p>
     * Defaults to a {@link SequentialPerAggregatePolicy}.
     *
     * @param policyBuilder The builder function to create the policy to use
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerDefaultSequencingPolicy(
            Function<Configuration, SequencingPolicy<? super EventMessage<?>>> policyBuilder);

    /**
     * Registers the builder function to create the Message Monitor for the {@link EventProcessor} of the given name.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorBuilder The builder function to use
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    default EventProcessingConfigurer registerMessageMonitor(String name,
                                                             Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
        return registerMessageMonitorFactory(name,
                                             (configuration, componentType, componentName) -> messageMonitorBuilder
                                                     .apply(configuration));
    }

    /**
     * Registers the factory to create the Message Monitor for the {@link EventProcessor} of the given name.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorFactory The factory to use
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerMessageMonitorFactory(String name,
                                                            MessageMonitorFactory messageMonitorFactory);

    /**
     * Registers a function that builds Rollback Configuration for given processor {@code name}.
     *
     * @param name                         The name of the event processor
     * @param rollbackConfigurationBuilder A function that builds Rollback Configuration
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerRollbackConfiguration(String name,
                                                            Function<Configuration, RollbackConfiguration> rollbackConfigurationBuilder);

    /**
     * Registers a {@link TransactionManager} for the {@link EventProcessor} of the given {@code name}.
     *
     * @param name                      The name of the event processor
     * @param transactionManagerBuilder The {@link TransactionManager} to use of the {@link EventProcessor} with the
     *                                  given {@code name}
     * @return an instance of Event Processing Configurer for fluent interfacing
     */
    EventProcessingConfigurer registerTransactionManager(String name,
                                                         Function<Configuration, TransactionManager> transactionManagerBuilder);

    /**
     * Builds the Event Processing Configuration.
     *
     * @return the Event Processing Configuration
     */
    EventProcessingConfiguration configure();
}
