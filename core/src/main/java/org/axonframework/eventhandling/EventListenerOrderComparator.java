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

import org.axonframework.common.Assert;

import java.util.Comparator;

/**
 * Comparator implementation that uses an {@link OrderResolver} instance to define the expected order of two
 * candidates. When two values have identical order, but the instances are not equal, this Comparator will use the
 * classes hashCode or their Identity HashCode (see {@link System#identityHashCode(Object)}) to order the instances.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class EventListenerOrderComparator implements Comparator<EventListener> {

    private final OrderResolver orderResolver;

    /**
     * Creates a comparator using given {@code orderResolver} to resolve the "order" value.
     *
     * @param orderResolver resolver that provides the "order" value of a given EventListener
     */
    public EventListenerOrderComparator(OrderResolver orderResolver) {
        Assert.notNull(orderResolver, "An orderResolver instance is mandatory");
        this.orderResolver = orderResolver;
    }

    @Override
    public int compare(EventListener o1, EventListener o2) {
        if (o1 == o2 || o1.equals(o2)) {
            return 0;
        }
        int order1 = orderResolver.orderOf(o1);
        int order2 = orderResolver.orderOf(o2);
        if (order1 != order2) {
            return Long.compare(order1, order2);
        }

        int hc1 = o1.hashCode();
        int hc2 = o2.hashCode();
        if (hc1 != hc2) {
            return Long.compare(hc1, hc2);
        }

        if (o1.getClass().equals(o2.getClass())) {
            // last resort.. this should really give different values
            int ihc1 = System.identityHashCode(o1);
            int ihc2 = System.identityHashCode(o2);

            return Long.compare(ihc1, ihc2);
        }
        return o1.getClass().getName().compareTo(o2.getClass().getName());
    }
}
