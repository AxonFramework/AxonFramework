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

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Comparator.comparing;

public class EventHandlingConfiguration implements ModuleConfiguration {

    private List<Component<Object>> eventHandlers = new ArrayList<>();
    private Map<String, EventProcessorBuilder> eventProcessors = new HashMap<>();
    private EventProcessorBuilder defaultEventProcessorBuilder =
            (conf, name, eh) -> new SubscribingEventProcessor(name,
                                                              new SimpleEventHandlerInvoker(eh),
                                                              conf.eventBus(),
                                                              conf.messageMonitor(SubscribingEventProcessor.class, name));
    private List<ProcessorSelector> selectors = new ArrayList<>();
    private ProcessorSelector defaultSelector;

    private Configuration config;
    private List<EventProcessor> initializedProcessors = new ArrayList<>();

    public static EventHandlingConfiguration assigningHandlersByPackage() {
        return new EventHandlingConfiguration().byDefaultAssignTo(o -> o.getClass().getPackage().getName());
    }

    private EventHandlingConfiguration() {
    }

    public EventHandlingConfiguration registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder) {
        defaultEventProcessorBuilder = eventProcessorBuilder;
        return this;
    }

    public EventHandlingConfiguration registerEventProcessor(String name, EventProcessorBuilder eventProcessorBuilder) {
        eventProcessors.put(name, eventProcessorBuilder);
        return this;
    }

    public EventHandlingConfiguration byDefaultAssignTo(String name) {
        defaultSelector = new ProcessorSelector(name, Integer.MIN_VALUE, r -> true);
        return this;
    }

    public EventHandlingConfiguration byDefaultAssignTo(Function<Object, String> assignmentFunction) {
        defaultSelector = new ProcessorSelector(Integer.MIN_VALUE, assignmentFunction.andThen(Optional::of));
        return this;
    }

    public EventHandlingConfiguration assignHandlersMatching(String name, Predicate<Object> criteria) {
        return assignHandlersMatching(name, 0, criteria);
    }

    public EventHandlingConfiguration assignHandlersMatching(String name, int priority, Predicate<Object> criteria) {
        selectors.add(new ProcessorSelector(name, priority, criteria));
        return this;
    }

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
            String processor = selectors.stream().map(s -> s.select(handler))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .orElse(defaultSelector.select(handler).orElseThrow(IllegalStateException::new));
            assignments.computeIfAbsent(processor, k -> new ArrayList<>()).add(handler);
        });

        assignments.forEach((name, handlers) -> {
            initializedProcessors.add(eventProcessors.getOrDefault(name, defaultEventProcessorBuilder)
                                              .createEventProcessor(config, name, handlers));
        });
    }


    @Override
    public void start() {
        initializedProcessors.forEach(EventProcessor::start);
    }

    @Override
    public void shutdown() {
        initializedProcessors.forEach(EventProcessor::shutdown);
    }

    @FunctionalInterface
    public interface EventProcessorBuilder {

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
