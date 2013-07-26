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
     * Creates a comparator using given <code>orderResolver</code> to resolve the "order" value.
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
            return order1 - order2;
        }

        int hc1 = o1.hashCode();
        int hc2 = o2.hashCode();
        if (hc1 != hc2) {
            return hc2 - hc1;
        }

        if (o1.getClass().equals(o2.getClass())) {
            // last resort.. this should really give different values
            int ihc1 = System.identityHashCode(o1);
            int ihc2 = System.identityHashCode(o2);

            return ihc2 - ihc1;
        }
        return o1.getClass().getName().compareTo(o2.getClass().getName());
    }
}
