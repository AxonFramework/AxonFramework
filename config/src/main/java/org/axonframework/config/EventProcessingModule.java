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

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A base {@link Module} and {@link ModuleBuilder} interface for event processing application modules.
 * <p>
 * The {@code EventProcessingModule} provides a foundation for event processing configuration, wherein event handlers
 * and sagas can be registered. Specialized implementations can extend this interface to provide specific event
 * processor configuration capabilities.
 * <p>
 * This module follows a builder paradigm with different phases for structured configuration.
 * <p>
 * Here's an example of a basic event processing module:
 * <pre>
 * EventProcessingModule.named("my-event-processing-module")
 *                      .eventHandlers()
 *                      .eventHandler(config -> new MyEventHandler())
 *                      .saga(MySaga.class);
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
     * Allows users to initiate event handler configuration through the {@link #eventHandlers()} method.
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
    }

    /**
     * The event handler configuration phase of the event processing module.
     * <p>
     * Event handlers and sagas registered in this phase will be managed by event processors. The specific type of
     * event processor depends on the implementation of the event processing module.
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

        /**
         * Configures a default processing group for event handlers that don't have explicit assignment.
         * <p>
         * This method allows setting a default processing group name that will be used for event handlers
         * that are not explicitly assigned to a specific processing group.
         *
         * @param processingGroup The default processing group name.
         * @return The event handler phase of this module, for a fluent API.
         */
        EventHandlerPhase defaultProcessingGroup(@Nonnull String processingGroup);
    }
} 