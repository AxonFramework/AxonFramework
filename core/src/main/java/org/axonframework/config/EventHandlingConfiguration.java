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

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Comparator.comparing;

/**
 * Module Configuration implementation that defines an Event Handling component. Typically, such a configuration
 * consists of a number of Event Handlers, which are assigned to one or more Event Processor that define the
 * transactional semantics of the processing.
 */
public class EventHandlingConfiguration implements ModuleConfiguration {

    private final List<Component<Object>> eventHandlers = new ArrayList<>();
    private final List<BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>>> defaultHandlerInterceptors = new ArrayList<>();
    private final Map<String, List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>>> handlerInterceptors = new HashMap<>();
    private final Map<String, EventProcessorBuilder> eventProcessors = new HashMap<>();
    private final List<ProcessorSelector> selectors = new ArrayList<>();
    private final List<EventProcessor> initializedProcessors = new ArrayList<>();
    private EventProcessorBuilder defaultEventProcessorBuilder = this::defaultEventProcessor;
    private ProcessorSelector defaultSelector;

    private Configuration config;

    /**
     * Creates a default configuration for an Event Handling module that assigns Event Handlers to Subscribing Event
     * Processors based on the package they're in. For each package found, a new Event Processor instance is created,
     * with the package name as the processor name. This default behavior can be overridden in the instance returned.
     * <p>
     * At a minimum, the Event Handler beans need to be registered before this component is useful.
     */
    public EventHandlingConfiguration() {
        byDefaultAssignTo(o -> {
            Class<?> handlerType = o.getClass();
            Optional<Map<String, Object>> annAttr = AnnotationUtils.findAnnotationAttributes(handlerType, ProcessingGroup.class);
            return annAttr
                    .map(attr -> (String) attr.get("processingGroup"))
                    .orElseGet(() -> handlerType.getPackage().getName());
        });
    }

    private SubscribingEventProcessor defaultEventProcessor(Configuration conf, String name, List<?> eh) {
        return subscribingEventProcessor(conf, name, eh, Configuration::eventBus);
    }

    private SubscribingEventProcessor subscribingEventProcessor(Configuration conf, String name, List<?> eh,
                                                                Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        return new SubscribingEventProcessor(name,
                                             new SimpleEventHandlerInvoker(eh,
                                                                           conf.parameterResolverFactory(),
                                                                           conf.getComponent(
                                                                                   ListenerInvocationErrorHandler.class,
                                                                                   LoggingErrorHandler::new)),
                                             messageSource.apply(conf),
                                             DirectEventProcessingStrategy.INSTANCE,
                                             PropagatingErrorHandler.INSTANCE,
                                             conf.messageMonitor(SubscribingEventProcessor.class,
                                                                 name));
    }

    /**
     * Returns the list of Message Handler Interceptors registered for the given {@code processorName}.
     *
     * @param configuration The main configuration
     * @param processorName The name of the processor to retrieve interceptors for
     * @return a list of Interceptors
     * @see EventHandlingConfiguration#registerHandlerInterceptor(BiFunction)
     * @see EventHandlingConfiguration#registerHandlerInterceptor(String, Function)
     */
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(Configuration configuration,
                                                                                    String processorName) {
        List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new ArrayList<>();
        defaultHandlerInterceptors.stream()
                .map(f -> f.apply(configuration, processorName))
                .filter(Objects::nonNull)
                .forEach(interceptors::add);
        handlerInterceptors.getOrDefault(processorName, Collections.emptyList())
                .stream()
                .map(f -> f.apply(configuration))
                .filter(Objects::nonNull)
                .forEach(interceptors::add);
        interceptors.add(new CorrelationDataInterceptor<>(configuration.correlationDataProviders()));
        return interceptors;
    }

    /**
     * Configure the use of Tracking Event Processors, instead of the default Subscribing ones. Tracking processors
     * work in their own thread(s), making processing asynchronous from the publication process.
     * <p>
     * The processor will use the {@link TokenStore} implementation provided in the global Configuration, and will
     * default to an {@link InMemoryTokenStore} when no Token Store was defined. Note that it is not recommended to use
     * the in-memory TokenStore in a production environment.
     *
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration usingTrackingProcessors() {
        return registerEventProcessorFactory(
                (conf, name, handlers) -> buildTrackingEventProcessor(conf, name, handlers, Configuration::eventBus));
    }

    /**
     * Register a TrackingProcessor using default configuration for the given {@code name}. Unlike
     * {@link #usingTrackingProcessors()}, this method will not default all processors to tracking, but instead only
     * use tracking for event handler that have been assigned to the processor with given {@code name}.
     * <p>
     * Events will be read from the EventBus (or EventStore) registered with the main configuration
     *
     * @param name The name of the processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerTrackingProcessor(String name) {
        return registerTrackingProcessor(name, Configuration::eventBus);
    }

    /**
     * Registers a TrackingProcessor using the given {@code source} to read messages from.
     *
     * @param name   The name of the TrackingProcessor
     * @param source The source of messages for this processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerTrackingProcessor(String name, Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return registerEventProcessor(name, (conf, n, handlers) ->
                buildTrackingEventProcessor(conf, name, handlers, source));
    }

    private EventProcessor buildTrackingEventProcessor(Configuration conf, String name, List<?> handlers,
                                                       Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return new TrackingEventProcessor(name, new SimpleEventHandlerInvoker(handlers,
                                                                              conf.parameterResolverFactory(),
                                                                              conf.getComponent(
                                                                                      ListenerInvocationErrorHandler.class,
                                                                                      LoggingErrorHandler::new)),
                                          source.apply(conf),
                                          conf.getComponent(TokenStore.class, InMemoryTokenStore::new),
                                          conf.getComponent(TransactionManager.class, NoTransactionManager::instance),
                                          conf.messageMonitor(EventProcessor.class, name));
    }

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
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder) {
        this.defaultEventProcessorBuilder = eventProcessorBuilder;
        return this;
    }

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
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerEventProcessor(String name, EventProcessorBuilder eventProcessorBuilder) {
        eventProcessors.put(name, eventProcessorBuilder);
        return this;
    }

    /**
     * Registers the Event Processor name to assign Event Handler beans to when no other, more explicit, rule matches.
     *
     * @param name The Event Processor name to assign Event Handlers to, by default.
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration byDefaultAssignTo(String name) {
        defaultSelector = new ProcessorSelector(name, Integer.MIN_VALUE, r -> true);
        return this;
    }

    /**
     * Register the given {@code interceptorBuilder} to build an Message Handling Interceptor for the Event Processor
     * with given {@code processorName}.
     * <p>
     * The {@code interceptorBuilder} may return {@code null}, in which case the return value is ignored.
     * <p>
     * Note that a CorrelationDataInterceptor is registered by default. To change correlation data attached to messages,
     * see {@link Configurer#configureCorrelationDataProviders(Function)}.
     *
     * @param processorName      The name of the processor to register the interceptor on
     * @param interceptorBuilder The function providing the interceptor to register, or {@code null}
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerHandlerInterceptor(String processorName,
                                                                 Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        handlerInterceptors
                .computeIfAbsent(processorName, k -> new ArrayList<>())
                .add(interceptorBuilder);
        return this;
    }

    /**
     * Register the given {@code interceptorBuilder} to build an Message Handling Interceptor for Event Processors
     * created in this configuration.
     * <p>
     * The {@code interceptorBuilder} is invoked once for each processor created, and may return {@code null}, in which
     * case the return value is ignored.
     * <p>
     * Note that a CorrelationDataInterceptor is registered by default. To change correlation data attached to messages,
     * see {@link Configurer#configureCorrelationDataProviders(Function)}.
     *
     * @param interceptorBuilder The builder function that provides an interceptor for each available processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerHandlerInterceptor(BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        defaultHandlerInterceptors.add(interceptorBuilder);
        return this;
    }

    /**
     * Registers a function that defines the Event Processor name to assign Event Handler beans to when no other rule
     * matches.
     *
     * @param assignmentFunction The function that returns a Processor Name for each Event Handler bean
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration byDefaultAssignTo(Function<Object, String> assignmentFunction) {
        defaultSelector = new ProcessorSelector(Integer.MIN_VALUE, assignmentFunction.andThen(Optional::of));
        return this;
    }

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Event Processor
     * with given {@code name}, with neutral priority (value 0).
     * <p>
     * Note that, when beans match multiple criteria for different processors with equal priority, the outcome is
     * undefined.
     *
     * @param name     The name of the Event Processor to assign matching Event Handlers to
     * @param criteria The criteria for Event Handler to match
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration assignHandlersMatching(String name, Predicate<Object> criteria) {
        return assignHandlersMatching(name, 0, criteria);
    }

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Event Processor
     * with given {@code name}, with given {@code priority}. Rules with higher value of {@code priority} take precedence
     * over those with a lower value.
     * <p>
     * Note that, when beans match multiple criteria for different processors with equal priority, the outcome is
     * undefined.
     *
     * @param name     The name of the Event Processor to assign matching Event Handlers to
     * @param priority The priority for this rule.
     * @param criteria The criteria for Event Handler to match
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration assignHandlersMatching(String name, int priority, Predicate<Object> criteria) {
        selectors.add(new ProcessorSelector(name, priority, criteria));
        return this;
    }

    /**
     * Register an Event Handler Bean with this configuration. The builder function receives the global Configuration
     * and is expected to return a fully initialized Event Handler bean, which is to be assigned to an Event Processor
     * using configured rules.
     *
     * @param eventHandlerBuilder The builder function for the Event Handler bean
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerEventHandler(Function<Configuration, Object> eventHandlerBuilder) {
        eventHandlers.add(new Component<>(() -> config, "eventHandler", eventHandlerBuilder));
        return this;
    }

    @Override
    public void initialize(Configuration config) {
        selectors.sort(comparing(ProcessorSelector::getPriority).reversed());
        this.config = config;

        Map<String, List<Object>> assignments = new HashMap<>();

        eventHandlers.stream().map(Component::get).forEach(handler -> {
            String processor =
                    selectors.stream().map(s -> s.select(handler)).filter(Optional::isPresent).map(Optional::get)
                            .findFirst()
                            .orElse(defaultSelector.select(handler).orElseThrow(IllegalStateException::new));
            assignments.computeIfAbsent(processor, k -> new ArrayList<>()).add(handler);
        });

        assignments.forEach((name, handlers) -> {
            EventProcessor eventProcessor = eventProcessors.getOrDefault(name, defaultEventProcessorBuilder)
                    .createEventProcessor(config, name, handlers);
            interceptorsFor(config, name).forEach(eventProcessor::registerInterceptor);
            initializedProcessors.add(eventProcessor);
        });
    }

    @Override
    public void start() {
        initializedProcessors.forEach(EventProcessor::start);
    }

    @Override
    public void shutdown() {
        initializedProcessors.forEach(EventProcessor::shutDown);
    }

    /**
     * Register a subscribing processor with given {@code name} that subscribes to the Event Bus.
     *
     * @param name The name of the Event Processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerSubscribingEventProcessor(String name) {
        return registerEventProcessor(
                name, (conf, n, eh) -> subscribingEventProcessor(conf, n, eh, Configuration::eventBus));
    }

    /**
     * Register a subscribing processor with given {@code name} that subscribes to the given {@code messageSource}.
     * This allows the use of standard Subscribing Processors that listen to another source than the Event Bus.
     *
     * @param name          The name of the Event Processor
     * @param messageSource The source the processor should read from
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerSubscribingEventProcessor(
            String name,
            Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        return registerEventProcessor(
                name,
                (c, n, eh) -> subscribingEventProcessor(c, n, eh, messageSource));
    }

    /**
     * Interface describing a Builder function for Event Processors.
     *
     * @see #createEventProcessor(Configuration, String, List)
     */
    @FunctionalInterface
    public interface EventProcessorBuilder {

        /**
         * Builder function for an Event Processor.
         *
         * @param configuration The global configuration the implementation may use to obtain dependencies
         * @param name          The name of the Event Processor to create
         * @param eventHandlers The Event Handler beans assigned to this processor
         * @return a fully initialized Event Processor
         */
        EventProcessor createEventProcessor(Configuration configuration, String name, List<?> eventHandlers);

    }

    private static class ProcessorSelector {

        private final int priority;
        private final Function<Object, Optional<String>> function;

        private ProcessorSelector(int priority, Function<Object, Optional<String>> selectorFunction) {
            this.priority = priority;
            this.function = selectorFunction;
        }

        private ProcessorSelector(String name, int priority, Predicate<Object> criteria) {
            this(priority, handler -> {
                if (criteria.test(handler)) {
                    return Optional.of(name);
                }
                return Optional.empty();
            });
        }

        public Optional<String> select(Object handler) {
            return function.apply(handler);
        }

        public int getPriority() {
            return priority;
        }
    }
}
