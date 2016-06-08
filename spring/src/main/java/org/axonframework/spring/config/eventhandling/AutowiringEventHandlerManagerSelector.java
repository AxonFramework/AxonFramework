/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventListener;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.*;

/**
 * EventProcessorSelector implementation that uses a Spring Application Context to find all selector beans available.
 * It uses the {@link Ordered} interface to allow custom ordering of selectors. Selectors that do not implement the Ordered
 * interface, are assumed to have an order of 0.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AutowiringEventHandlerManagerSelector implements EventHandlerManagerSelector, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final List<EventHandlerManagerSelector> selectors = new ArrayList<>();
    private volatile boolean initialized;

    @Override
    public EventHandlerInvoker selectHandlerManager(EventListener eventListener) {
        if (!initialized) {
            initialize();
        }
        EventHandlerInvoker eventHandlerInvoker = null;
        Iterator<EventHandlerManagerSelector> selectorIterator = selectors.iterator();
        while (eventHandlerInvoker == null && selectorIterator.hasNext()) {
            eventHandlerInvoker = selectorIterator.next().selectHandlerManager(eventListener);
        }
        return eventHandlerInvoker;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void initialize() {
        if (initialized) {
            return;
        }

        this.initialized = true;
        Map<String, EventHandlerManagerSelector> candidates = applicationContext.getBeansOfType(EventHandlerManagerSelector.class);
        SortedSet<OrderedEventProcessorSelector> orderedCandidates = new TreeSet<>();
        for (Map.Entry<String, EventHandlerManagerSelector> entry : candidates.entrySet()) {
            if (entry.getValue() != this) {
                orderedCandidates.add(new OrderedEventProcessorSelector(entry.getKey(), entry.getValue()));
            }
        }
        for (OrderedEventProcessorSelector candidate : orderedCandidates) {
            selectors.add(candidate.selector);
        }
        if (selectors.isEmpty()) {
            selectors.add(new DefaultEventHandlerManagerSelector());
        }
    }

    private static final class OrderedEventProcessorSelector implements Comparable<OrderedEventProcessorSelector> {

        private final String name;
        private final EventHandlerManagerSelector selector;
        private final int order;

        private OrderedEventProcessorSelector(String name, EventHandlerManagerSelector selector) {
            this.name = name;
            this.selector = selector;
            if (selector instanceof Ordered) {
                order = ((Ordered) selector).getOrder();
            } else if (selector.getClass().isAnnotationPresent(Order.class)) {
                order = selector.getClass().getAnnotation(Order.class).value();
            } else {
                order = 0;
            }
        }

        @Override
        public int compareTo(OrderedEventProcessorSelector o) {
            if (this.order == o.order) {
                return this.name.compareTo(o.name);
            } else {
                return (this.order < o.order) ? -1 : (1);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OrderedEventProcessorSelector that = (OrderedEventProcessorSelector) o;

            return order == that.order && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + order;
            return result;
        }
    }
}
