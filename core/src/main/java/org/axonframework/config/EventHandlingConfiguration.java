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

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;

import java.util.*;
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
    private final Map<String, EventProcessorBuilder> eventProcessors = new HashMap<>();
    private final List<ProcessorSelector> selectors = new ArrayList<>();
    private final List<EventProcessor> initializedProcessors = new ArrayList<>();
    private EventProcessorBuilder defaultEventProcessorBuilder = this::defaultEventProcessor;
    private ProcessorSelector defaultSelector;

    private Configuration config;

    /**
     * Creates a default configuration for an Event Handling module that assigns Event Handlers to Subscribing Event
     * Processors based on the package they're in. FOr each package found, a new Event Processor instance is created,
     * with the package name as the processor name. This default behavior can be overridden in the instance returned.
     * <p>
     * At a minimum, the Event Handler beans need to be registered before this component is useful.
     */
    public EventHandlingConfiguration() {
        byDefaultAssignTo(o -> o.getClass().getPackage().getName());
    }

    private SubscribingEventProcessor defaultEventProcessor(Configuration conf, String name, List<?> eh) {
        return new SubscribingEventProcessor(name,
                                             new SimpleEventHandlerInvoker(eh,
                                                                           conf.getComponent(
                                                                                   ListenerErrorHandler.class,
                                                                                   LoggingListenerErrorHandler::new)),
                                             conf.eventBus(),
                                             conf.messageMonitor(SubscribingEventProcessor.class,
                                                                 name));
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
        return registerEventProcessorFactory(this::buildTrackingEventProcessor);
    }

    /**
     * Register a TrackingProcessor using default configuration for the given {@code name}. Unlike
     * {@link #usingTrackingProcessors()}, this method will not default to tracking, but instead only use tracking for
     * event handler that have been assigned to the processor with given {@code name}.
     *
     * @param name The name of the processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerTrackingProcessor(String name) {
        return registerEventProcessor(name, this::buildTrackingEventProcessor);
    }

    private EventProcessor buildTrackingEventProcessor(Configuration conf, String name, List<?> handlers) {
        TrackingEventProcessor processor = new TrackingEventProcessor(name, new SimpleEventHandlerInvoker(handlers,
                                                                                                          conf.getComponent(
                                                                                                                  ListenerErrorHandler.class,
                                                                                                                  LoggingListenerErrorHandler::new)),
                                                                      conf.eventBus(),
                                                                      conf.getComponent(TokenStore.class,
                                                                                        InMemoryTokenStore::new),
                                                                      conf.getComponent(TransactionManager.class,
                                                                                        NoTransactionManager::instance),
                                                                      conf.messageMonitor(EventProcessor.class,
                                                                                          name));
        CorrelationDataInterceptor<EventMessage<?>> interceptor = new CorrelationDataInterceptor<>();
        interceptor.registerCorrelationDataProviders(conf.correlationDataProviders());
        processor.registerInterceptor(interceptor);
        processor.registerInterceptor(new TransactionManagingInterceptor<>(conf.getComponent(TransactionManager.class, NoTransactionManager::instance)));
        return processor;
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
        Collections.sort(selectors, comparing(ProcessorSelector::getPriority).reversed());
        this.config = config;

        Map<String, List<Object>> assignments = new HashMap<>();

        eventHandlers.stream().map(Component::get).forEach(handler -> {
            String processor =
                    selectors.stream().map(s -> s.select(handler)).filter(Optional::isPresent).map(Optional::get)
                            .findFirst()
                            .orElse(defaultSelector.select(handler).orElseThrow(IllegalStateException::new));
            assignments.computeIfAbsent(processor, k -> new ArrayList<>()).add(handler);
        });

        assignments.forEach((name, handlers) -> initializedProcessors
                .add(eventProcessors.getOrDefault(name, defaultEventProcessorBuilder)
                             .createEventProcessor(config, name, handlers)));
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
