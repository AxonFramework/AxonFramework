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

import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.monitoring.MessageMonitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Holds a registry of all event processors created during configuration of sagas and event handlers. It is responsible
 * for registering {@link EventProcessor}s, {@link EventHandlerInvoker}s, {@link MessageHandlerInterceptor}s, {@link
 * ErrorHandler}s and {@link MessageMonitor}s.
 *
 * @author Milan Savic
 * @since 3.3
 */
public interface EventProcessorRegistry {

    /**
     * Registers handler invoker within this registry which will be assigned to the event processor with given {@code
     * processorName} during the initialization phase.
     *
     * @param processorName              The name of the Event Processor
     * @param eventHandlerInvokerBuilder Builder which builds event handler invoker. Builder can use configuration to
     *                                   obtain other components
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry registerHandlerInvoker(String processorName,
                                                  Function<Configuration, EventHandlerInvoker> eventHandlerInvokerBuilder);

    /**
     * Defines the Event Processor builder for an Event Processor with the given {@code name}. Event Processors
     * registered using this method have priority over those defined in
     * {@link #registerEventProcessorFactory(EventProcessorBuilder)}.
     * <p>
     * The given builder is expected to create a fully initialized Event Processor implementation based on the name and
     * list of event handler beans. The builder also received the global configuration instance, from which it can
     * retrieve components.
     * <p>
     * Note that the processor must be initialized, but shouldn't be started yet. The processor's
     * {@link EventProcessor#start()} method is invoked when the global configuration is started.
     *
     * @param name                  The name of the Event Processor for which to use this builder
     * @param eventProcessorBuilder The builder function for the Event Processor
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry registerEventProcessor(String name, EventProcessorBuilder eventProcessorBuilder);

    /**
     * Register the given {@code interceptorBuilder} to build an Message Handling Interceptor for the Event Processor
     * with given {@code processorName}.
     * <p>
     * The {@code interceptorBuilder} may return {@code null}, in which case the return value is ignored.
     *
     * @param processorName      The name of the processor to register the interceptor on
     * @param interceptorBuilder The function providing the interceptor to register, or {@code null}
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry registerHandlerInterceptor(String processorName,
                                                      Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder);

    /**
     * Returns a list of interceptors for a processor with given {@code processorName}.
     *
     * @param processorName The name of the processor
     * @return a list of interceptors for a processor with given {@code processorName}
     */
    List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(String processorName);

    /**
     * Allows for more fine-grained definition of the Event Processor to use for each group of Event Listeners. The
     * given builder is expected to create a fully initialized Event Processor implementation based on the name and
     * list of event handler beans. The builder also received the global configuration instance, from which it can
     * retrieve components.
     * <p>
     * Note that the processor must be initialized, but shouldn't be started yet. The processor's
     * {@link EventProcessor#start()} method is invoked when the global configuration is started.
     *
     * @param eventProcessorBuilder The builder function for the Event Processor
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder);

    /**
     * Configures the default {@link org.axonframework.eventhandling.ErrorHandler} for any
     * {@link org.axonframework.eventhandling.EventProcessor}. This can be overridden per EventProcessor by calling the
     * {@link #configureErrorHandler(String, Function)} function.
     *
     * @param errorHandlerBuilder The {@link org.axonframework.eventhandling.ErrorHandler} to use for the
     *                            {@link org.axonframework.eventhandling.EventProcessor} with the given {@code name}
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry configureErrorHandler(Function<Configuration, ErrorHandler> errorHandlerBuilder);

    /**
     * Configures a {@link org.axonframework.eventhandling.ErrorHandler} for the
     * {@link org.axonframework.eventhandling.EventProcessor} of the given {@code name}. This
     * overrides the default ErrorHandler configured through the {@link org.axonframework.config.Configurer}.
     *
     * @param name                The name of the event processor
     * @param errorHandlerBuilder The {@link org.axonframework.eventhandling.ErrorHandler} to use for the
     *                            {@link org.axonframework.eventhandling.EventProcessor} with the given {@code name}
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry configureErrorHandler(String name,
                                                 Function<Configuration, ErrorHandler> errorHandlerBuilder);

    /**
     * Configures the builder function to create the Message Monitor for the {@link EventProcessor} of the given name.
     * This overrides any Message Monitor configured through {@link Configurer}.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorBuilder The builder function to use
     * @return event processor registry for chaining purposes
     */
    default EventProcessorRegistry configureMessageMonitor(String name,
                                                           Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
        return configureMessageMonitor(name,
                                       (configuration, componentType, componentName) -> messageMonitorBuilder
                                               .apply(configuration));
    }

    /**
     * Configures the factory to create the Message Monitor for the {@link EventProcessor} of the given name. This
     * overrides any Message Monitor configured through {@link Configurer}.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorFactory The factory to use
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry configureMessageMonitor(String name, MessageMonitorFactory messageMonitorFactory);

    /**
     * Register a TrackingEventProcessor using default configuration for the given {@code name}. Unlike
     * {@link #usingTrackingProcessors()}, this method will not default all processors to tracking, but instead only
     * use tracking for event handler that have been assigned to the processor with given {@code name}.
     * <p>
     * Events will be read from the EventBus (or EventStore) registered with the main configuration
     *
     * @param name The name of the processor
     * @return event processor registry for chaining purposes
     */
    default EventProcessorRegistry registerTrackingEventProcessor(String name) {
        return registerTrackingEventProcessorUsingSource(name, Configuration::eventBus);
    }

    /**
     * Registers a TrackingEventProcessor using the given {@code source} to read messages from.
     *
     * @param name   The name of the TrackingEventProcessor
     * @param source The source of messages for this processor
     * @return event processor registry for chaining purposes
     */
    default EventProcessorRegistry registerTrackingEventProcessorUsingSource(String name,
                                                                             Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return registerTrackingEventProcessor(name,
                                              source,
                                              c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                                                  TrackingEventProcessorConfiguration::forSingleThreadedProcessing));
    }

    /**
     * Registers a TrackingEventProcessor with the given {@code name}, reading from the Event Bus (or Store) from the
     * main configuration and using the given {@code processorConfiguration}. The given {@code sequencingPolicy} defines
     * the policy for events that need to be executed sequentially.
     *
     * @param name                   The name of the Tracking Processor
     * @param processorConfiguration The configuration for the processor
     * @return event processor registry for chaining purposes
     */
    default EventProcessorRegistry registerTrackingEventProcessor(String name,
                                                                  Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration) {
        return registerTrackingEventProcessor(name, Configuration::eventBus, processorConfiguration);
    }

    /**
     * Registers a TrackingEventProcessor with the given {@code name}, reading from the given {@code source} and using
     * the given {@code processorConfiguration}. The given {@code sequencingPolicy} defines the policy for events that
     * need to be executed sequentially.
     *
     * @param name                   The name of the Tracking Processor
     * @param source                 The source to read Events from
     * @param processorConfiguration The configuration for the processor
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry registerTrackingEventProcessor(String name,
                                                          Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                          Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration);

    /**
     * Register a subscribing event processor with given {@code name} that subscribes to the Event Bus.
     *
     * @param name The name of the Event Processor
     * @return event processor registry for chaining purposes
     */
    default EventProcessorRegistry registerSubscribingEventProcessor(String name) {
        return registerSubscribingEventProcessor(name, Configuration::eventBus);
    }

    /**
     * Register a subscribing event processor with given {@code name} that subscribes to the given {@code
     * messageSource}. This allows the use of standard Subscribing Event Processors that listen to another source than
     * the Event Bus.
     *
     * @param name          The name of the Event Processor
     * @param messageSource The source the processor should read from
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry registerSubscribingEventProcessor(String name,
                                                             Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource);

    /**
     * Configure the use of Tracking Event Processors, instead of the default Subscribing ones. Tracking processors
     * work in their own thread(s), making processing asynchronous from the publication process.
     * <p>
     * The processor will use the {@link TokenStore} implementation provided in the global Configuration, and will
     * default to an {@link InMemoryTokenStore} when no Token Store was defined. Note that it is not recommended to use
     * the in-memory TokenStore in a production environment.
     * <p>
     * The processors will use the a {@link TrackingEventProcessorConfiguration} registered with the configuration, or
     * otherwise to a single threaded configuration (which means the processor will run in a single Thread and a batch
     * size of 1).
     *
     * @return event processor registry for chaining purposes
     */
    default EventProcessorRegistry usingTrackingProcessors() {
        return usingTrackingProcessors(c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                                           TrackingEventProcessorConfiguration::forSingleThreadedProcessing));
    }

    /**
     * Configure the use of Tracking Event Processors, instead of the default Subscribing ones. Tracking processors
     * work in their own thread(s), making processing asynchronous from the publication process.
     * <p>
     * The processor will use the {@link TokenStore} implementation provided in the global Configuration, and will
     * default to an {@link InMemoryTokenStore} when no Token Store was defined. Note that it is not recommended to use
     * the in-memory TokenStore in a production environment.
     *
     * @param config The configuration for the processors to use
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry usingTrackingProcessors(Function<Configuration, TrackingEventProcessorConfiguration> config);

    /**
     * Register the TokenStore to use for a processor of given {@code name}.
     * <p>
     * If no explicit TokenStore implementation is available for a Processor, it is taken from the main Configuration.
     * <p>
     * Note that this configuration is ignored if the processor with given name isn't a Tracking Processor.
     *
     * @param name       The name of the processor to configure the token store for
     * @param tokenStore The function providing the TokenStore based on a given Configuration
     * @return event processor registry for chaining purposes
     */
    EventProcessorRegistry registerTokenStore(String name, Function<Configuration, TokenStore> tokenStore);

    /**
     * Obtains all registered event processors. This method is to be called after Event Processor Registry is
     * initialized.
     *
     * @return map of registered event processors within this registry where processor name is the key
     */
    Map<String, EventProcessor> eventProcessors();

    /**
     * Obtains event processor by name. This method is to be called after Event Processor Registry is initialized.
     *
     * @param name The name of the event processor
     * @return optional whether event processor with given name exists
     */
    @SuppressWarnings("unchecked")
    default <T extends EventProcessor> Optional<T> eventProcessor(String name) {
        Map<String, EventProcessor> eventProcessors = eventProcessors();
        if (eventProcessors.containsKey(name)) {
            return (Optional<T>) Optional.of(eventProcessors.get(name));
        }
        return Optional.empty();
    }

    /**
     * Returns the Event Processor with the given {@code name}, if present and of the given {@code expectedType}. This
     * method also returns an empty optional if the Processor was configured, but it hasn't been assigned any Event
     * Handlers.
     *
     * @param name         The name of the processor to return
     * @param expectedType The type of processor expected
     * @param <T>          The type of processor expected
     * @return an Optional referencing the processor, if present and of expected type.
     */
    default <T extends EventProcessor> Optional<T> eventProcessor(String name, Class<T> expectedType) {
        return eventProcessor(name).filter(expectedType::isInstance).map(expectedType::cast);
    }

    /**
     * Initializes the Event Processor Registry with global configuration. Initializing means that all event processor
     * are instantiated and wired to corresponding event handler invokers. After initialization it is safe to start the
     * processors.
     *
     * @param config Global configuration
     */
    void initialize(Configuration config);

    /**
     * Starts the event processors.
     */
    void start();

    /**
     * Shuts down the event processors.
     */
    void shutdown();

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
}
