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

package org.axonframework.eventhandling;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Cluster selector implementation that uses a Spring Application Context to find all selector beans available. It uses
 * the {@link Ordered} interface to allow custom ordering of selectors. Selectors that do not implement the Ordered
 * interface, are assumed to have an order of 0.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AutowiringClusterSelector implements ClusterSelector, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final List<ClusterSelector> selectors = new ArrayList<>();
    private volatile boolean initialized;

    @Override
    public Cluster selectCluster(EventListener eventListener) {
        if (!initialized) {
            initialize();
        }
        Cluster cluster = null;
        Iterator<ClusterSelector> selectorIterator = selectors.iterator();
        while (cluster == null && selectorIterator.hasNext()) {
            cluster = selectorIterator.next().selectCluster(eventListener);
        }
        return cluster;
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
        Map<String, ClusterSelector> candidates = applicationContext.getBeansOfType(ClusterSelector.class);
        SortedSet<OrderedClusterSelector> orderedCandidates = new TreeSet<>();
        for (Map.Entry<String, ClusterSelector> entry : candidates.entrySet()) {
            if (entry.getValue() != this) {
                orderedCandidates.add(new OrderedClusterSelector(entry.getKey(), entry.getValue()));
            }
        }
        for (OrderedClusterSelector candidate : orderedCandidates) {
            selectors.add(candidate.selector);
        }
        if (selectors.isEmpty()) {
            selectors.add(new DefaultClusterSelector());
        }
    }

    private static final class OrderedClusterSelector implements Comparable<OrderedClusterSelector> {

        private final String name;
        private final ClusterSelector selector;
        private final int order;

        private OrderedClusterSelector(String name, ClusterSelector selector) {
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
        public int compareTo(OrderedClusterSelector o) {
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

            OrderedClusterSelector that = (OrderedClusterSelector) o;

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
