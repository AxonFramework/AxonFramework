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

package org.axonframework.config;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Module} and {@link ModuleBuilder} implementation providing operations to construct an event processing
 * application module.
 * <p>
 * The {@code EventProcessingModule} follows a builder paradigm, wherein event handlers, sagas, and event processors
 * can be registered in a structured way through different phases.
 * <p>
 * To initiate event handler registration, you should move into the handler registration phase by invoking
 * {@link SetupPhase#eventHandlers()}. To register event processors, a similar registration phase switch should be made
 * by invoking {@link SetupPhase#eventProcessors()}.
 * <p>
 * Here's an example of how to register event handlers and configure event processors:
 * <pre>
 * EventProcessingModule.named("my-event-processing-module")
 *                      .eventHandlers()
 *                      .eventHandler(config -> new MyEventHandler())
 *                      .saga(MySaga.class)
 *                      .eventProcessors()
 *                      .subscribingEventProcessor("my-subscribing-processor")
 *                      .pooledStreamingEventProcessor("my-streaming-processor",
 *                                                    config -> config.eventStore());
 * </pre>
 * <p>
 * Note that users do not have to invoke {@link #build()} themselves when using this interface, as the
 * {@link org.axonframework.configuration.ApplicationConfigurer} takes care of that.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventProcessingModule extends Module, ModuleBuilder<EventProcessingModule> {

    /**
     * Starts an {@code EventProcessingModule} with the given {@code moduleName}.
     *
     * @param moduleName The name of the {@code EventProcessingModule} under construction.
     * @return The setup phase of this module, for a fluent API.
     */
    static SetupPhase named(@Nonnull String moduleName) {
        return new SimpleEventProcessingModule(moduleName);
    }

    /**
     * The setup phase of the event processing module.
     * <p>
     * Allows for two paths when building an event processing module. Firstly, the {@link #eventHandlers()} method
     * allows users to start configuring event handlers and sagas for this module. The second option allows for moving
     * to the {@link #eventProcessors()} configuration flow of this module.
     */
    interface SetupPhase {

        /**
         * Initiates the event handler configuration phase for this module.
         *
         * @return The event handler phase of this module, for a fluent API.
         */
        EventHandlerPhase eventHandlers();

        /**
         * Initiates the event handler configuration phase for this module, as well as performing the given
         * {@code configurationLambda} within this phase.
         *
         * @param configurationLambda A consumer of the event handler phase, performing event handler configuration
         *                            right away.
         * @return The event handler phase of this module, for a fluent API.
         */
        default EventHandlerPhase eventHandlers(@Nonnull Consumer<EventHandlerPhase> configurationLambda) {
            EventHandlerPhase eventHandlerPhase = eventHandlers();
            requireNonNull(configurationLambda, "The event handler configuration lambda cannot be null.")
                    .accept(eventHandlerPhase);
            return eventHandlerPhase;
        }

        /**
         * Initiates the event processor configuration phase for this module.
         *
         * @return The event processor phase of this module, for a fluent API.
         */
        EventProcessorPhase eventProcessors();

        /**
         * Initiates the event processor configuration phase for this module, as well as performing the given
         * {@code configurationLambda} within this phase.
         *
         * @param configurationLambda A consumer of the event processor phase, performing event processor configuration
         *                            right away.
         * @return The event processor phase of this module, for a fluent API.
         */
        default EventProcessorPhase eventProcessors(@Nonnull Consumer<EventProcessorPhase> configurationLambda) {
            EventProcessorPhase eventProcessorPhase = eventProcessors();
            requireNonNull(configurationLambda, "The event processor configuration lambda cannot be null.")
                    .accept(eventProcessorPhase);
            return eventProcessorPhase;
        }
    }

    /**
     * The event handler configuration phase of the event processing module.
     * <p>
     * Every registered event handler will be subscribed to the appropriate event processors configured in this module.
     * Sagas registered in this phase will also be managed by the configured event processors.
     */
    interface EventHandlerPhase extends SetupPhase, ModuleBuilder<EventProcessingModule> {

        /**
         * Registers an event handler with this module.
         * <p>
         * The event handler will be assigned to an event processor based on the configured processing group assignment
         * rules.
         *
         * @param eventHandlerBuilder A builder that creates the event handler instance.
         * @return The event handler phase of this module, for a fluent API.
         */
        EventHandlerPhase eventHandler(@Nonnull ComponentBuilder<Object> eventHandlerBuilder);

        /**
         * Registers a saga type with this module.
         * <p>
         * The saga will be automatically configured with appropriate event handling and will be assigned to an event
         * processor based on the configured processing group assignment rules.
         *
         * @param sagaType The saga class to register.
         * @param <T>      The type of the saga.
         * @return The event handler phase of this module, for a fluent API.
         */
        <T> EventHandlerPhase saga(@Nonnull Class<T> sagaType);

        /**
         * Registers a saga type with this module, providing additional configuration through the given
         * {@code sagaConfigurer}.
         * <p>
         * The saga will be automatically configured with appropriate event handling and will be assigned to an event
         * processor based on the configured processing group assignment rules.
         *
         * @param sagaType        The saga class to register.
         * @param sagaConfigurer  A consumer to configure the saga.
         * @param <T>             The type of the saga.
         * @return The event handler phase of this module, for a fluent API.
         */
        <T> EventHandlerPhase saga(@Nonnull Class<T> sagaType, 
                                   @Nonnull Consumer<SagaConfigurer<T>> sagaConfigurer);

        /**
         * Registers an event handler invoker with this module.
         * <p>
         * This allows for more advanced event handler configuration where you want to provide your own
         * {@link EventHandlerInvoker} implementation.
         *
         * @param processingGroup        The processing group this invoker belongs to.
         * @param eventHandlerInvokerBuilder A builder that creates the event handler invoker.
         * @return The event handler phase of this module, for a fluent API.
         */
        EventHandlerPhase eventHandlerInvoker(@Nonnull String processingGroup,
                                              @Nonnull ComponentBuilder<EventHandlerInvoker> eventHandlerInvokerBuilder);
    }

    /**
     * The event processor configuration phase of the event processing module.
     * <p>
     * This phase allows for the configuration of different types of event processors that will handle the events
     * for the event handlers and sagas registered in this module.
     */
    interface EventProcessorPhase extends SetupPhase, ModuleBuilder<EventProcessingModule> {

        /**
         * Registers a subscribing event processor with this module.
         * <p>
         * The subscribing event processor will subscribe to events from the configured event bus and process them
         * in the publishing thread.
         *
         * @param processorName The name of the event processor.
         * @return The subscribing event processor configuration phase, for a fluent API.
         */
        SubscribingEventProcessorPhase subscribingEventProcessor(@Nonnull String processorName);

        /**
         * Registers a pooled streaming event processor with this module.
         * <p>
         * The pooled streaming event processor will stream events from the configured event store and process them
         * using a thread pool for improved performance.
         *
         * @param processorName The name of the event processor.
         * @return The pooled streaming event processor configuration phase, for a fluent API.
         */
        PooledStreamingEventProcessorPhase pooledStreamingEventProcessor(@Nonnull String processorName);

        /**
         * Registers a custom event processor with this module.
         * <p>
         * This allows for registering event processors that are not of the standard types provided by the framework.
         *
         * @param processorName           The name of the event processor.
         * @param eventProcessorBuilder   A builder that creates the event processor instance.
         * @return The event processor phase of this module, for a fluent API.
         */
        EventProcessorPhase customEventProcessor(@Nonnull String processorName,
                                                 @Nonnull ComponentBuilder<EventProcessor> eventProcessorBuilder);
    }

    /**
     * Configuration phase for a subscribing event processor.
     */
    interface SubscribingEventProcessorPhase extends EventProcessorPhase {

        /**
         * Configures the message source for this subscribing event processor.
         * <p>
         * If not specified, the default event bus will be used.
         *
         * @param messageSourceBuilder A builder that creates the message source.
         * @return The subscribing event processor configuration phase, for a fluent API.
         */
        SubscribingEventProcessorPhase messageSource(
                @Nonnull ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSourceBuilder);

        /**
         * Configures the subscribing event processor using the provided builder.
         *
         * @param processorConfigurer A consumer that configures the SubscribingEventProcessor.Builder.
         * @return The subscribing event processor configuration phase, for a fluent API.
         */
        SubscribingEventProcessorPhase configureSubscribing(@Nonnull Consumer<SubscribingEventProcessor.Builder> processorConfigurer);
    }

    /**
     * Configuration phase for a pooled streaming event processor.
     */
    interface PooledStreamingEventProcessorPhase extends EventProcessorPhase {

        /**
         * Configures the event source for this pooled streaming event processor.
         * <p>
         * This is typically the event store from which events will be streamed.
         *
         * @param eventSourceBuilder A builder that creates the event source.
         * @return The pooled streaming event processor configuration phase, for a fluent API.
         */
        PooledStreamingEventProcessorPhase eventSource(
                @Nonnull ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSourceBuilder);

        /**
         * Configures the token store for this pooled streaming event processor.
         * <p>
         * If not specified, the default token store will be used.
         *
         * @param tokenStoreBuilder A builder that creates the token store.
         * @return The pooled streaming event processor configuration phase, for a fluent API.
         */
        PooledStreamingEventProcessorPhase tokenStore(@Nonnull ComponentBuilder<TokenStore> tokenStoreBuilder);

        /**
         * Configures the pooled streaming event processor using the provided builder.
         *
         * @param processorConfigurer A consumer that configures the PooledStreamingEventProcessor.Builder.
         * @return The pooled streaming event processor configuration phase, for a fluent API.
         */
        PooledStreamingEventProcessorPhase configurePooledStreaming(@Nonnull Consumer<PooledStreamingEventProcessor.Builder> processorConfigurer);
    }
} 